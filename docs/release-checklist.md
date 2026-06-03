<h1 align="center">Чеклист альфа-релиза</h1>

Целевой релиз: `v0.9.6-alpha.1`

<h2 align="center">Область релиза</h2>
- [x] Выбраны тег и версия релиза: `0.9.6-alpha.1`
- [x] Журнал изменений обновлен для этого релиза
- [x] Черновик release notes подготовлен
- [ ] GitHub Release создан из тега `v0.9.6-alpha.1`

<h2 align="center">Качество кода</h2>
- [ ] Frontend: `npm --prefix frontend ci`
- [ ] Frontend: `npm --prefix frontend run lint`
- [ ] Frontend: `npm --prefix frontend run typecheck`
- [ ] Frontend: `npm --prefix frontend run test:ci`
- [ ] Frontend: `npm --prefix frontend run test:smoke`
- [ ] Frontend: `npm --prefix frontend run test:e2e`
- [ ] Frontend: `npm --prefix frontend run build`
- [ ] Python: `cd backend/python && pip install -r requirements.txt -r requirements-dev.txt`
- [ ] Python: `cd backend/python && python -m ruff check .`
- [ ] Python: `cd backend/python && python -m pytest`
- [ ] Java: `cd backend/java && mvn -B test`
- [ ] Java: `cd backend/java && mvn -B package -DskipTests`

<h2 align="center">Diagnostics validation</h2>
- [ ] Python diagnostics handles empty trades, all-loss trades, all-win trades, and missing equity curves.
- [ ] Max drawdown and recovery calculations are covered by tests.
- [ ] Low sample, high drawdown, negative expectancy, unstable segment, no-trades, all-losing, and unavailable profit-factor warnings are visible.
- [ ] Java/Python contract accepts optional `diagnostics`.
- [ ] `GET /api/runs/{id}` and `GET /api/runs/{id}/result` return diagnostics when present.
- [ ] Старые runs без diagnostics возвращаются корректно с `diagnostics=null`/absent.
- [ ] Frontend `/runs/[id]` показывает Strategy Report и graceful missing state.
- [ ] Frontend `/compare` сравнивает max drawdown, win rate, profit factor, trade count, diagnostics status, warnings count, and stability status.


<h2 align="center">Live risk hardening validation</h2>
- [ ] Portfolio exposure summary reflects current synced positions and open orders.
- [ ] Cross-symbol exposure blocks orders above session max position notional.
- [ ] Session daily notional guard includes accepted/filled/submitted same-day orders.
- [ ] Realized intraday loss guard blocks new orders after configured loss threshold.
- [ ] Limit-order slippage guard rejects prices outside the configured percentage from latest market price.
- [ ] Market-data staleness guard rejects stale price snapshots before adapter submission.
- [ ] Risk audit shows explicit rejection reasons for exposure, daily notional, loss, slippage, and stale market data.

<h2 align="center">Проверка артефактов</h2>
- [ ] `strategy-report-{runId}.json` создается для успешного run.
- [ ] Report artifact содержит run id, strategy id/version id, dataset id, metrics, diagnostics, warnings, generatedAt.
- [ ] Report artifact содержит safety note.
- [ ] `GET /api/runs/{id}/artifacts` показывает strategy report artifact.
- [ ] `GET /api/runs/{id}/artifacts/{artifactId}/download` скачивает report JSON.
- [ ] `scripts/export-openapi-artifacts.sh 0.9.6-alpha.1` записывает:
  - `artifacts/openapi-java-v0.9.6-alpha.1.json`
  - `artifacts/openapi-python-v0.9.6-alpha.1.json`

<h2 align="center">Проверка запуска</h2>
- [ ] `docker compose config -q`
- [ ] `docker compose up --build` запускает все сервисы
- [ ] Проверка frontend: `http://localhost:3000`
- [ ] Проверка Java API: `http://localhost:18080/api/health`
- [ ] Проверка Python parser: `http://localhost:18000/health`
- [ ] `scripts/docker-compose-smoke.sh` завершается успешно
- [ ] `scripts/collect-diagnostics.sh` создает diagnostics bundle и не падает при unavailable services
- [ ] Security scans видимы в CI или локальном отчете

<h2 align="center">Конфигурация и безопасность</h2>
- [ ] Учетные данные для целевого окружения настроены и не являются локальными заглушками
- [ ] `SECURITY_JWT_SECRET` не равен `change-me` вне локальной разработки
- [ ] `PYTHON_PARSER_INTERNAL_SECRET` не равен `change-me` вне локальной разработки
- [ ] `LIVE_TRADING_CREDENTIAL_ENCRYPTION_KEY` не равен `change-me`, если live trading включен
- [ ] `LIVE_TRADING_REAL_ORDER_SUBMISSION_ENABLED=false`, если нет отдельного production approval
- [ ] Переменные Telegram integration заданы только при включенной функции
- [ ] Файлы `.env` не закоммичены

<h2 align="center">Safety notes</h2>
- [ ] Backtest diagnostics are documented as research artifacts, not trading signals.
- [ ] Strategy reports do not imply production readiness.
- [ ] Testnet/backtest validation is not production exchange certification.
- [ ] Real order submission remains disabled by default.

<h2 align="center">Rollback</h2>
- [ ] Последний известный исправный тег зафиксирован перед promotion.
- [ ] Database rollback не требует migration revert, так как diagnostics хранится в существующих JSON payloads.
- [ ] При rollback удалить/игнорировать generated strategy report artifacts для affected runs.
- [ ] Release notes содержат limitations и rollback/validation notes.

<h2 align="center">Завершение релиза</h2>
- [ ] Коммит релиза отправлен в `main`
- [ ] Тег релиза создан
- [ ] Tagged alpha release workflow прошел успешно
- [ ] Все release issues закрыты или явно перенесены в future work
