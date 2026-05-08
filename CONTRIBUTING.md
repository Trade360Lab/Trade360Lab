# Contributing to Trade360Lab

## 1. Introduction

Trade360Lab is a monorepo for a trading research and execution platform. It combines a Next.js frontend, a Java control plane, a Python execution plane, and PostgreSQL as the main persistence layer.

This document explains how to contribute safely and efficiently. It is written for both internal developers and external contributors who want to understand the repository, make focused changes, and submit clean pull requests.

We welcome contributors who can improve working flows, keep the architecture coherent, and prefer practical, testable changes over speculative rewrites.

## 2. Project Structure

The repository is organized as a monorepo with clear runtime boundaries.

- `frontend/`
  - Next.js application.
  - Contains the user interface, client-side features, API integration, and workspace screens.
- `backend/java/`
  - Spring Boot service.
  - Acts as the control plane: public API, orchestration, run management, strategy metadata, and result persistence.
- `backend/python/`
  - FastAPI service plus local execution modules.
  - Acts as the execution and data plane: candle import, strategy validation, strategy execution, and backtesting logic.
- `backend/java/src/main/resources/schema.sql`
  - Java-side database schema initialization.
- `backend/python/parser/schema.sql`
  - Python-side schema initialization for parser-owned tables.
- `docker-compose.yml`
  - Main entry point for running the full stack locally.
- `docs/`
  - Project documentation, release notes, and supporting documents.
- `CHANGELOG.md`
  - Release history and notable changes.

The Java service uses a Flyway migration baseline for release-safe schema evolution while keeping the existing SQL initialization file aligned for simple alpha startup.

## 3. Getting Started

### Prerequisites

Install the following tools before contributing:

- Node.js 20+ and npm
- Java 17+
- Maven 3.9+
- Python 3.11+
- Docker and Docker Compose
- PostgreSQL client tools are optional but useful for debugging

### Install dependencies

Frontend:

```bash
cd frontend
npm install
```

Python backend:

```bash
cd backend/python
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Java backend:

```bash
cd backend/java
mvn test -q
```

### Run the full project with Docker

This is the preferred way to start the full stack:

```bash
docker compose up --build
```

Default service endpoints:

- Frontend: `http://localhost:3000`
- Java API: `http://localhost:18080`
- Python service: `http://localhost:18000`
- PostgreSQL: `localhost:55432`

### Run services locally

Frontend:

```bash
cd frontend
npm run dev
```

Python service:

```bash
cd backend/python
source .venv/bin/activate
uvicorn parser.main:app --host 0.0.0.0 --port 8000
```

Java service:

```bash
cd backend/java
mvn spring-boot:run
```

Use local service execution when you are working on one area and want faster iteration than Docker rebuilds.

## 4. Development Workflow

Use short-lived branches for each task.

Recommended branch prefixes:

- `feature/...`
- `fix/...`
- `chore/...`
- `docs/...`
- `refactor/...`
- `test/...`

Examples:

- `feature/run-snapshots`
- `fix/python-execution-timeout`
- `chore/update-dev-docs`

Workflow:

1. Create a focused branch from the main integration branch.
2. Understand the existing implementation before adding code.
3. Prefer extending current entities, services, and flows instead of introducing parallel ones.
4. Keep changes scoped to one problem.
5. Run tests locally before opening a PR.

If you touch multiple layers, keep the flow vertical and intentional. For example, if you add a new run field, update the schema, entity, DTO, service, and tests in one coherent change.

## 5. Commit Convention

Trade360Lab uses Conventional Commits.

Format:

```text
type(scope): message
```

Examples:

- `feat(backend/java): add run execution flow`
- `feat(backend/python): add execution endpoint`
- `fix(backend/java): handle python timeout`
- `chore(backend/db): add run snapshot table`
- `docs(root): update changelog`

Supported types:

- `feat`
- `fix`
- `refactor`
- `chore`
- `docs`
- `test`

Common scopes:

- `backend/java`
- `backend/python`
- `backend/db`
- `frontend`
- `root`

Guidelines:

- Use the smallest accurate scope.
- Keep commit messages concrete.
- Avoid vague messages such as `fix stuff` or `update code`.
- Split unrelated changes into separate commits.

## 6. Pull Request Guidelines

Every PR should be atomic.

Do:

- Keep one PR focused on one task or one closely related vertical flow.
- Explain what changed.
- Explain why the change is needed.
- Explain how to verify it.
- Link the related issue if one exists.

Do not:

- Mix schema changes, UI redesign, and unrelated refactors in one PR.
- Hide risky architecture changes inside a bug fix PR.

Recommended PR description:

- `What was changed`
- `Why it was changed`
- `How to test`
- `Screenshots` if frontend behavior changed
- `Breaking changes` if any

Checklist before merge:

- Tests pass
- Lint or static checks pass where applicable
- New logic has tests
- API changes are reflected in the relevant layer
- `CHANGELOG.md` is updated when the change affects releases or user-visible behavior

Release issues:

- Use the release task template for alpha release scope.
- Close implemented release issues from commits or PR descriptions with `Closes #...`.
- Repeat the live trading safety checklist when touching credentials, risk gates, audit export, certification reports, or order submission.
- Real order submission must remain disabled by default.
- Testnet certification reports are alpha validation artifacts, not production exchange certification.

## 7. Coding Standards

### Java

- Follow the existing Spring Boot structure: controller, service, repository, entity, dto.
- Use DTOs at API boundaries.
- Keep controllers thin.
- Keep orchestration and business logic in services.
- Do not mix persistence concerns into controllers.
- Do not bypass existing abstractions unless there is a strong reason.

### Python

- Follow FastAPI and service-oriented structure already used in `backend/python`.
- Type hints are expected for new code.
- Keep API layer, DTOs, repositories, and services separated.
- Put execution logic in services, not in endpoint functions.
- Prefer explicit result contracts over ad hoc dictionaries.

### Frontend

- Use TypeScript for all new code.
- Keep ESLint and type checking clean.
- Follow existing Next.js structure and feature boundaries.
- Avoid UI-only fixes that silently break API expectations.

## 8. Testing

Run relevant tests before opening a PR.

Java:

```bash
cd backend/java
mvn test
```

Python:

```bash
cd backend/python
source .venv/bin/activate
pytest
```

Frontend:

```bash
cd frontend
npm test
```

Useful frontend checks:

```bash
cd frontend
npm run lint
npm run typecheck
```

Minimum expectation:

- Every new behavior should have at least one test.
- Bug fixes should include a regression test when practical.
- If a change affects an end-to-end flow, prefer integration-style coverage over isolated mocks only.

## 9. Database Changes

Database changes must be conservative.

Current project state:

- Java schema evolution uses Flyway files in `backend/java/src/main/resources/db/migration`.
- `backend/java/src/main/resources/schema.sql` remains aligned for simple local alpha startup.
- Python parser-owned schema is initialized from `backend/python/parser/schema.sql`.

Rules for schema changes:

- Add new tables and columns in a backward-compatible way.
- Prefer additive changes over destructive ones.
- Do not rename or remove columns casually.
- Update the owning service model, repository, DTOs, and tests together.
- Avoid changes that break existing Docker startup or test initialization.

If both Java and Python rely on a table, check both code paths before changing the schema.

## 10. Release Process

The project uses alpha-stage semantic versioning such as:

- `v0.2.0-alpha.1`
- `v0.3.0-alpha.1`

Release notes should be reflected in `CHANGELOG.md`.

Release tags may be created manually or through CI, depending on the release workflow used at the time.

Releases may also carry semantic milestone names, for example:

- `Reproducible Execution Engine`
- `Observability Foundation`
- `Multi-User & Security Base`

If your change materially affects release behavior, architecture, public API, or reproducibility, it should be visible in the changelog.

## 11. Architecture Principles

Keep these principles in mind when contributing:

- Java is the control plane.
- Python is the execution plane.
- Keep coupling between services as low as possible.
- Do not introduce microservices prematurely.
- Prefer one working end-to-end flow over incomplete abstraction layers.
- Optimize after the flow works, not before.

In practice, this means:

- API and orchestration belong in Java.
- Execution-heavy logic belongs in Python.
- Shared behavior should be expressed through clear contracts, not duplicated logic.

## 12. What NOT to Do

- Do not duplicate entities that already exist.
- Do not create parallel run, result, or dataset models unless the current model is truly insufficient.
- Do not break existing APIs without a clear reason and explicit coordination.
- Do not add Kafka, Celery, distributed workers, or other heavy infrastructure without a real need.
- Do not over-engineer for hypothetical future scale.
- Do not write “perfect architecture” that does not deliver a working flow.
- Do not rewrite large modules when a minimal extension is enough.

## 13. Communication

Ask questions early if the ownership or intent of a change is unclear.

When discussing changes:

- Be concrete
- Be technical
- Be constructive
- Explain tradeoffs
- Prefer evidence over opinion

Good discussion topics:

- Why a new field is needed
- Why a contract should change
- How to keep backward compatibility
- How to test a vertical flow safely

If you challenge an approach, propose a better one with clear reasoning.

Thanks for contributing to Trade360Lab.
