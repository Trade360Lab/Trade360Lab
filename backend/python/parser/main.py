import logging
import time
import traceback
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from parser.candles.repositories.candle_repository import CandleRepository
from parser.common.config.db import get_connection, initialize_schema
from parser.common.config.settings import settings
from parser.common.exceptions import AppError
from parser.common.util.logging import bind_log_context, configure_logging
from parser.imports.dto.candle_import_dto import CandleImportRequest, CandleImportResponse
from parser.imports.repositories.candle_import_repository import CandleImportRepository
from parser.imports.services.candle_import_service import CandleImportService
from parser.runs.dto.run_execute_dto import RunExecuteRequest, RunExecuteResponse
from parser.runs.services.strategy_execution_service import ENGINE_VERSION, StrategyExecutionService
from parser.strategies.dto.strategy_validation_dto import (
    StrategyValidationRequest,
    StrategyValidationResponse,
)
from parser.strategies.services.strategy_validation_service import StrategyValidationService

logger = logging.getLogger(__name__)


class HealthResponse(BaseModel):
    status: str = Field(description="Service health status.", examples=["ok"])
    service: str = Field(description="Service identifier.", examples=["python-parser"])


class ReadinessResponse(BaseModel):
    status: str = Field(description="Parser readiness status.", examples=["ready", "degraded"])
    service: str = Field(description="Service identifier.", examples=["python-parser"])
    parser_version: str = Field(alias="parserVersion", serialization_alias="parserVersion")
    database: str = Field(
        description="Database connectivity status.",
        examples=["ok", "unavailable"],
    )
    engine_version: str = Field(alias="engineVersion", serialization_alias="engineVersion")
    internal_auth_configured: str = Field(
        alias="internalAuthConfigured",
        serialization_alias="internalAuthConfigured",
        description="Whether internal auth is configured without exposing the secret.",
    )


class ErrorResponse(BaseModel):
    status: str = Field(description="Error status.", examples=["error"])
    message: str = Field(
        description="Human-readable error message.",
        examples=["Dataset was not found"],
    )


def create_app() -> FastAPI:
    configure_logging()

    app = FastAPI(
        title="TradeLab Python Parser API",
        version="0.9.6-alpha.1",
        description="Internal API for candle imports, strategy validation, and strategy execution.",
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        openapi_tags=[
            {"name": "health", "description": "Service health endpoints."},
            {"name": "imports", "description": "Market candle import operations."},
            {"name": "strategies", "description": "Strategy validation endpoints."},
            {"name": "runs", "description": "Strategy execution endpoints."},
        ],
    )

    @app.middleware("http")
    async def add_correlation_context(request: Request, call_next):
        correlation_id = request.headers.get("X-Correlation-Id") or f"py-{uuid4().hex}"
        run_id = request.headers.get("X-Run-Id")
        job_id = request.headers.get("X-Job-Id")
        started_monotonic = time.perf_counter()
        with bind_log_context(correlation_id=correlation_id, run_id=run_id, job_id=job_id):
            if request.url.path.startswith("/internal/"):
                internal_secret = request.headers.get("X-Internal-Auth")
                if internal_secret != settings.internal_shared_secret:
                    logger.warning(
                        "Rejected internal request with invalid shared secret",
                        extra={
                            "event": "http_request_rejected",
                            "method": request.method,
                            "path": request.url.path,
                        },
                    )
                    response = JSONResponse(
                        status_code=401,
                        content={"status": "error", "message": "Unauthorized internal request"},
                    )
                    response.headers["X-Correlation-Id"] = correlation_id
                    if run_id:
                        response.headers["X-Run-Id"] = run_id
                    if job_id:
                        response.headers["X-Job-Id"] = job_id
                    return response
            logger.info(
                "Incoming HTTP request",
                extra={
                    "event": "http_request_started",
                    "method": request.method,
                    "path": request.url.path,
                },
            )
            try:
                response = await call_next(request)
            except Exception:  # noqa: BLE001
                logger.exception(
                    "Unhandled HTTP request error",
                    extra={
                        "event": "http_request_failed",
                        "method": request.method,
                        "path": request.url.path,
                        "execution_duration_ms": int(
                            (time.perf_counter() - started_monotonic) * 1000
                        ),
                    },
                )
                raise
            logger.info(
                "Completed HTTP request",
                extra={
                    "event": "http_request_completed",
                    "method": request.method,
                    "path": request.url.path,
                    "status_code": response.status_code,
                    "execution_duration_ms": int((time.perf_counter() - started_monotonic) * 1000),
                },
            )
        response.headers["X-Correlation-Id"] = correlation_id
        if run_id:
            response.headers["X-Run-Id"] = run_id
        if job_id:
            response.headers["X-Job-Id"] = job_id
        return response

    @app.on_event("startup")
    async def on_startup() -> None:
        logger.info("Starting python-parser service on port %s", settings.python_service_port)
        try:
            connection = get_connection()
            try:
                initialize_schema(connection)
            finally:
                connection.close()
        except AppError:
            logger.warning("Database schema initialization skipped during startup", exc_info=True)

    @app.exception_handler(AppError)
    async def handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        logger.exception("Application error: %s", exc.message)
        return JSONResponse(
            status_code=exc.status_code,
            content={"status": "error", "message": exc.message},
        )

    @app.get(
        "/health",
        response_model=HealthResponse,
        tags=["health"],
        summary="Check parser health",
        description="Returns the current status of the Python parser service.",
    )
    async def healthcheck() -> HealthResponse:
        return HealthResponse(status="ok", service="python-parser")

    @app.get(
        "/readiness",
        response_model=ReadinessResponse,
        tags=["health"],
        summary="Check parser readiness",
        description="Returns parser readiness without exposing secrets.",
    )
    async def readiness() -> ReadinessResponse:
        database_status = "ok"
        try:
            connection = get_connection()
            try:
                with connection.cursor() as cursor:
                    cursor.execute("SELECT 1")
            finally:
                connection.close()
        except AppError:
            database_status = "unavailable"

        internal_auth_status = (
            "configured"
            if settings.internal_shared_secret != "change-me-parser-internal-secret"
            else "default-dev-secret"
        )
        return ReadinessResponse(
            status="ready" if database_status == "ok" else "degraded",
            service="python-parser",
            parserVersion="0.9.6-alpha.1",
            database=database_status,
            engineVersion=ENGINE_VERSION,
            internalAuthConfigured=internal_auth_status,
        )

    @app.post(
        "/internal/import/candles",
        response_model=CandleImportResponse,
        tags=["imports"],
        summary="Import candles",
        description="Fetches candles from the configured exchange and stores them in PostgreSQL.",
        responses={400: {"model": ErrorResponse}, 500: {"model": ErrorResponse}},
    )
    async def import_candles(request: CandleImportRequest) -> CandleImportResponse:
        logger.info("Incoming candle import request")
        connection = get_connection()
        try:
            repository = CandleImportRepository(connection)
            service = CandleImportService(repository)
            return service.import_candles(request)
        finally:
            connection.close()

    @app.post(
        "/internal/strategies/validate",
        response_model=StrategyValidationResponse,
        tags=["strategies"],
        summary="Validate strategy file",
        description=(
            "Loads a strategy file and validates its exported metadata and parameters schema."
        ),
        responses={400: {"model": ErrorResponse}, 500: {"model": ErrorResponse}},
    )
    async def validate_strategy(request: StrategyValidationRequest) -> StrategyValidationResponse:
        logger.info("Incoming strategy validation request for %s", request.file_path)
        service = StrategyValidationService()
        return service.validate(request.file_path)

    @app.post(
        "/internal/runs/execute",
        response_model=RunExecuteResponse,
        tags=["runs"],
        summary="Execute strategy run",
        description=(
            "Executes a strategy against stored candles for the requested range and parameters."
        ),
        responses={400: {"model": ErrorResponse}, 500: {"model": ErrorResponse}},
    )
    async def execute_run(request: RunExecuteRequest) -> RunExecuteResponse:
        with bind_log_context(
            correlation_id=request.correlation_id,
            run_id=request.run_id,
            job_id=request.job_id,
        ):
            logger.info(
                "Incoming strategy run request",
                extra={
                    "event": "run_request_received",
                    "user_id": request.user_id,
                    "strategy_id": request.strategy_id,
                    "strategy_version_id": request.strategy_version_id,
                    "strategy_file": Path(request.strategy_file_path).name,
                    "exchange": request.exchange,
                    "symbol": request.symbol,
                    "interval": request.interval,
                    "from_time": request.from_time,
                    "to_time": request.to_time,
                    "parameter_keys": sorted(request.params.keys()),
                },
            )

            connection = None
            started_monotonic = time.perf_counter()
            started_at = datetime.now(tz=UTC)
            try:
                connection = get_connection()
                repository = CandleRepository(connection)
                service = StrategyExecutionService(repository)
                return service.execute(request)
            except AppError as exc:
                finished_at = datetime.now(tz=UTC)
                logger.exception("Strategy run failed with application error")
                return RunExecuteResponse(
                    success=False,
                    summary=None,
                    metrics=None,
                    trades=[],
                    equity_curve=[],
                    artifacts=None,
                    engine_version=ENGINE_VERSION,
                    run_id=request.run_id,
                    job_id=request.job_id,
                    correlation_id=request.correlation_id,
                    started_at=started_at.isoformat().replace("+00:00", "Z"),
                    finished_at=finished_at.isoformat().replace("+00:00", "Z"),
                    execution_duration_ms=int((time.perf_counter() - started_monotonic) * 1000),
                    error_code="APP_ERROR",
                    error_message=exc.message,
                    stacktrace=traceback.format_exc(),
                    error=exc.message,
                )
            except Exception:  # noqa: BLE001
                finished_at = datetime.now(tz=UTC)
                logger.exception("Strategy run failed with unexpected error")
                return RunExecuteResponse(
                    success=False,
                    summary=None,
                    metrics=None,
                    trades=[],
                    equity_curve=[],
                    artifacts=None,
                    engine_version=ENGINE_VERSION,
                    run_id=request.run_id,
                    job_id=request.job_id,
                    correlation_id=request.correlation_id,
                    started_at=started_at.isoformat().replace("+00:00", "Z"),
                    finished_at=finished_at.isoformat().replace("+00:00", "Z"),
                    execution_duration_ms=int((time.perf_counter() - started_monotonic) * 1000),
                    error_code="UNEXPECTED_ERROR",
                    error_message="Unexpected execution error",
                    stacktrace=traceback.format_exc(),
                    error="Unexpected execution error",
                )
            finally:
                if connection is not None:
                    connection.close()

    return app


app = create_app()


if __name__ == "__main__":
    uvicorn.run("parser.main:app", host="0.0.0.0", port=settings.python_service_port, reload=False)
