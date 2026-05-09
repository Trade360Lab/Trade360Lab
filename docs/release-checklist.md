<h1 align="center">Чеклист альфа-релиза</h1>

Целевой релиз: `v0.9.2-alpha.1`

<h2 align="center">Область релиза</h2>
- [x] Выбраны тег и версия релиза: `0.9.2-alpha.1`
- [x] Журнал изменений обновлен для этого релиза
- [x] Черновик release notes подготовлен
- [ ] GitHub Release создан из тега `v0.9.2-alpha.1`

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

<h2 align="center">Проверка запуска</h2>
- [ ] `docker compose config -q`
- [ ] `docker compose up --build` запускает все сервисы
- [ ] Проверка frontend: `http://localhost:3000`
- [ ] Проверка Java API: `http://localhost:18080/api/health`
- [ ] Проверка Python parser: `http://localhost:18000/health`
- [ ] `scripts/docker-compose-smoke.sh` завершается успешно
- [ ] `scripts/export-openapi-artifacts.sh 0.9.2-alpha.1` записывает:
  - `artifacts/openapi-java-v0.9.2-alpha.1.json`
  - `artifacts/openapi-python-v0.9.2-alpha.1.json`
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

<h2 align="center">Завершение релиза</h2>
- [ ] Коммит релиза отправлен в `main`
- [ ] Тег релиза создан
- [ ] Tagged alpha release workflow прошел успешно
- [ ] Все release issues закрыты или явно перенесены в future work
- [ ] План rollback задокументирован
- [ ] В release notes указано, что real order submission остается выключенным по умолчанию
- [ ] В release notes указано, что testnet certification не означает production readiness
