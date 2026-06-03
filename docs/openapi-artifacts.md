<h1 align="center">OpenAPI-артефакты релиза</h1>

Релиз `v0.9.6-alpha.1` экспортирует API-контракты как build artifacts после запуска Java API и Python parser.

```bash
docker compose up --build -d
scripts/export-openapi-artifacts.sh 0.9.6-alpha.1
```

Принятое расположение артефактов:

- `artifacts/openapi-java-v0.9.6-alpha.1.json`
- `artifacts/openapi-python-v0.9.6-alpha.1.json`

Скрипт читает `JAVA_OPENAPI_URL` и `PYTHON_OPENAPI_URL`, если нужны нестандартные host/port, и ретраит загрузку, пока сервисы становятся ready.
