import type { Run, RunArtifact, RunMetrics, RunParams, RunStatus, Strategy } from "@/lib/types";
import { apiFetch } from "@/lib/api/client";
import { normalizeRunDiagnostics } from "@/lib/run-diagnostics";

type BackendRunResponse = {
  id: number;
  strategyId: number;
  strategyVersionId?: number | null;
  parameterPresetId?: number | null;
  status: string;
  startedAt?: string | null;
  exchange: string;
  symbol: string;
  interval: string;
  from: string;
  to: string;
  parameters?: Record<string, unknown> | null;
  params?: Record<string, unknown> | null;
  metrics?: Record<string, unknown> | null;
  diagnostics?: Run["diagnostics"];
  errorMessage?: string | null;
  createdAt: string;
  finishedAt?: string | null;
};

type BackendRunArtifactResponse = {
  id: number;
  artifactType: string;
  artifactName: string;
  contentType: string;
  sizeBytes?: number | null;
};

export type CreateRunPayload = {
  strategyId: number;
  strategyVersionId?: number | null;
  parameterPresetId?: number | null;
  exchange: string;
  symbol: string;
  interval: string;
  from: string;
  to: string;
  params: Record<string, unknown>;
};

async function readErrorMessage(response: Response) {
  try {
    const payload = (await response.json()) as { message?: string };
    if (typeof payload.message === "string" && payload.message.trim().length > 0) {
      return payload.message;
    }
  } catch {
    // Ignore JSON parsing issues and fallback to text/status below.
  }

  try {
    const text = await response.text();
    if (text.trim().length > 0) {
      return text;
    }
  } catch {
    // Ignore text parsing issues and fallback to status below.
  }

  return `Request failed with status ${response.status}`;
}

function toNumber(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return null;
}

function toPercent(value: unknown, options?: { negative?: boolean }) {
  const numberValue = toNumber(value);
  if (numberValue === null) {
    return 0;
  }

  const normalized = Math.abs(numberValue) <= 1 ? numberValue * 100 : numberValue;
  return options?.negative ? -Math.abs(normalized) : normalized;
}

function getMetric(metrics: Record<string, unknown> | null | undefined, keys: string[]) {
  if (!metrics) {
    return null;
  }

  for (const key of keys) {
    if (key in metrics) {
      return metrics[key];
    }
  }

  return null;
}

function toFrontendStatus(status: string): RunStatus {
  switch (status.toUpperCase()) {
    case "PENDING":
    case "CREATED":
    case "QUEUED":
    case "RETRYING":
      return "queued";
    case "RUNNING":
      return "running";
    case "SUCCESS":
    case "COMPLETED":
    case "SUCCEEDED":
      return "done";
    case "FAILED":
      return "failed";
    case "CANCELED":
      return "canceled";
    default:
      return "failed";
  }
}

function toFrontendMetrics(metrics: Record<string, unknown> | null | undefined): RunMetrics {
  return {
    pnl: toPercent(getMetric(metrics, ["pnl", "profit", "total_return", "return", "totalReturn"])),
    sharpe: toNumber(getMetric(metrics, ["sharpe", "sharpe_ratio", "sharpeRatio"])) ?? 0,
    maxDrawdown: toPercent(
      getMetric(metrics, ["maxDrawdown", "max_drawdown", "drawdown", "maxDrawdownPct"]),
      { negative: true }
    ),
    trades: Math.round(
      toNumber(getMetric(metrics, ["trades", "trade_count", "tradeCount", "total_trades"])) ?? 0
    ),
    winrate: toPercent(getMetric(metrics, ["winrate", "win_rate", "winRate"])),
    avgTrade: toPercent(
      getMetric(metrics, ["avgTrade", "avg_trade", "average_trade", "avg_trade_return"])
    ),
    feesImpact: toPercent(
      getMetric(metrics, ["feesImpact", "fees_impact", "commission", "commission_impact"]),
      { negative: true }
    ),
  };
}

function toRunParams(interval: string, from: string, to: string): RunParams {
  return {
    fees: "Backend managed",
    slippage: "Backend managed",
    execution: "Python service",
    riskPerTrade: "Backend managed",
    maxExposure: "Backend managed",
    symbols: [],
    timeframe: interval,
    period: `${from} -> ${to}`,
  };
}

function formatArtifactSize(sizeBytes: number | null | undefined) {
  if (!sizeBytes || sizeBytes <= 0) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB"];
  let value = sizeBytes;
  let index = 0;

  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }

  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function toArtifactType(artifactType: string): RunArtifact["type"] {
  if (artifactType.includes("CSV") || artifactType.includes("EXPORT")) {
    return "export";
  }
  if (artifactType.includes("REPORT") || artifactType.includes("SUMMARY")) {
    return "report";
  }
  return "log";
}

function toFrontendArtifact(artifact: BackendRunArtifactResponse, runId: number): RunArtifact {
  return {
    id: String(artifact.id),
    label: artifact.artifactName || artifact.artifactType,
    type: toArtifactType(artifact.artifactType),
    size: formatArtifactSize(artifact.sizeBytes),
    downloadUrl: `/api/runs/${runId}/artifacts/${artifact.id}/download`,
  };
}

function toDisplayStrategyName(
  run: BackendRunResponse,
  strategiesById?: Map<number, Strategy>
) {
  const strategy = strategiesById?.get(run.strategyId);
  if (!strategy) {
    return `strategy_${run.strategyId}.py`;
  }

  return strategy.name?.trim() || strategy.fileName;
}

function normalizeBackendRun(payload: unknown): BackendRunResponse | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const candidate = payload as Record<string, unknown>;
  if (
    typeof candidate.id !== "number" ||
    typeof candidate.strategyId !== "number" ||
    typeof candidate.status !== "string" ||
    typeof candidate.exchange !== "string" ||
    typeof candidate.symbol !== "string" ||
    typeof candidate.interval !== "string" ||
    typeof candidate.from !== "string" ||
    typeof candidate.to !== "string" ||
    typeof candidate.createdAt !== "string"
  ) {
    return null;
  }

  return {
    id: candidate.id,
    strategyId: candidate.strategyId,
    strategyVersionId:
      typeof candidate.strategyVersionId === "number" ? candidate.strategyVersionId : null,
    parameterPresetId:
      typeof candidate.parameterPresetId === "number" ? candidate.parameterPresetId : null,
    status: candidate.status,
    exchange: candidate.exchange,
    symbol: candidate.symbol,
    interval: candidate.interval,
    from: candidate.from,
    to: candidate.to,
    params:
      candidate.parameters && typeof candidate.parameters === "object"
        ? (candidate.parameters as Record<string, unknown>)
        : candidate.params && typeof candidate.params === "object"
          ? (candidate.params as Record<string, unknown>)
          : null,
    metrics:
      candidate.metrics && typeof candidate.metrics === "object"
        ? (candidate.metrics as Record<string, unknown>)
        : null,
    diagnostics: normalizeRunDiagnostics(candidate.diagnostics),
    errorMessage: typeof candidate.errorMessage === "string" ? candidate.errorMessage : null,
    createdAt: candidate.createdAt,
    startedAt: typeof candidate.startedAt === "string" ? candidate.startedAt : null,
    finishedAt: typeof candidate.finishedAt === "string" ? candidate.finishedAt : null,
  };
}

export function toFrontendRun(
  backendRun: BackendRunResponse,
  strategiesById?: Map<number, Strategy>
): Run {
  const strategy = strategiesById?.get(backendRun.strategyId);
  const configPayload = {
    strategyId: backendRun.strategyId,
    strategyVersionId: backendRun.strategyVersionId ?? null,
    parameterPresetId: backendRun.parameterPresetId ?? null,
    exchange: backendRun.exchange,
    symbol: backendRun.symbol,
    interval: backendRun.interval,
    from: backendRun.from,
    to: backendRun.to,
    params: backendRun.params ?? {},
  };

  return {
    id: String(backendRun.id),
    backendRunId: backendRun.id,
    strategyId: backendRun.strategyId,
    strategyVersionId: backendRun.strategyVersionId ?? null,
    parameterPresetId: backendRun.parameterPresetId ?? null,
    strategy: toDisplayStrategyName(backendRun, strategiesById),
    datasetVersion: `${backendRun.exchange}:${backendRun.symbol}`,
    period: `${backendRun.from} -> ${backendRun.to}`,
    timeframe: backendRun.interval,
    params: toRunParams(backendRun.interval, backendRun.from, backendRun.to),
    strategyParams: backendRun.params ?? {},
    metrics: toFrontendMetrics(backendRun.metrics),
    diagnostics: backendRun.diagnostics ?? null,
    status: toFrontendStatus(backendRun.status),
    artifacts: [],
    createdAt: backendRun.createdAt,
    finishedAt: backendRun.finishedAt,
    errorMessage: backendRun.errorMessage ?? null,
    exchange: backendRun.exchange,
    symbol: backendRun.symbol,
    from: backendRun.from,
    to: backendRun.to,
    commit: "backend-run",
    config: JSON.stringify(configPayload, null, 2),
    tags: strategy?.status === "VALID" ? [] : ["candidate"],
    diff: {
      code: false,
      data: false,
      config: false,
    },
  };
}

function toRuns(
  payload: unknown,
  strategiesById?: Map<number, Strategy>
) {
  if (!Array.isArray(payload)) {
    return [];
  }

  return payload
    .map((item) => normalizeBackendRun(item))
    .filter((item): item is BackendRunResponse => item !== null)
    .map((item) => toFrontendRun(item, strategiesById));
}

export async function fetchRuns(strategiesById?: Map<number, Strategy>) {
  const response = await apiFetch("/api/runs", {
    method: "GET",
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  const payload = (await response.json()) as unknown;
  return toRuns(payload, strategiesById);
}

export async function fetchRunById(id: number | string, strategiesById?: Map<number, Strategy>) {
  const response = await apiFetch(`/api/runs/${id}`, {
    method: "GET",
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  const payload = (await response.json()) as unknown;
  const backendRun = normalizeBackendRun(payload);
  if (!backendRun) {
    throw new Error("Invalid run response");
  }

  return toFrontendRun(backendRun, strategiesById);
}

export async function fetchRunArtifacts(runId: number): Promise<RunArtifact[]> {
  const response = await apiFetch(`/api/runs/${runId}/artifacts`, {
    method: "GET",
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  const payload = (await response.json()) as unknown;
  if (!Array.isArray(payload)) {
    return [];
  }

  return payload
    .filter(
      (item): item is BackendRunArtifactResponse =>
        item !== null &&
        typeof item === "object" &&
        typeof (item as BackendRunArtifactResponse).id === "number" &&
        typeof (item as BackendRunArtifactResponse).artifactType === "string" &&
        typeof (item as BackendRunArtifactResponse).artifactName === "string" &&
        typeof (item as BackendRunArtifactResponse).contentType === "string"
    )
    .map((artifact) => toFrontendArtifact(artifact, runId));
}

export async function createRun(payload: CreateRunPayload, strategiesById?: Map<number, Strategy>) {
  const response = await apiFetch("/api/runs", {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  const body = (await response.json()) as unknown;
  const backendRun = normalizeBackendRun(body);
  if (!backendRun) {
    throw new Error("Invalid run creation response");
  }

  return toFrontendRun(backendRun, strategiesById);
}

export async function retryRun(runId: number | string) {
  const response = await apiFetch(`/api/runs/${runId}/retry`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
}

export async function cancelRun(runId: number | string) {
  const response = await apiFetch(`/api/runs/${runId}/cancel`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
}
