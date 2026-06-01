<h1 align="center">Rollback релиза</h1>

Целевой релиз: `v0.9.5-alpha.1`

<h2 align="center">Триггеры</h2>

- Рабочий процесс релиза в CI падает после создания тега.
- Docker smoke validation завершается ошибкой.
- OpenAPI artifacts отсутствуют или имеют некорректный формат.
- Live trading safety guard блокирует старт в целевом окружении.

<h2 align="center">Порядок rollback</h2>

1. Оставить `LIVE_TRADING_REAL_ORDER_SUBMISSION_ENABLED=false`.
2. Объявить rollback в release channel.
3. Остановить текущий стек командой `docker compose down`.
4. Повторно развернуть последний известный исправный тег, сейчас это `v0.9.2-alpha.1`.
5. Выполнить `docker compose config -q`.
6. Выполнить `scripts/docker-compose-smoke.sh`.
7. Проверить:
   - `http://localhost:3000`
   - `http://localhost:18080/api/health`
   - `http://localhost:18080/api/readiness`
   - `http://localhost:18000/health`
   - `http://localhost:18000/readiness`
8. Задокументировать проблемный artifact, workflow или safety gate в GitHub issue и связать его с patch/follow-up release.

<h2 align="center">Заметки по данным</h2>

Этот alpha-релиз не содержит destructive migrations: diagnostics хранится в существующих JSON payloads и report artifacts. Если будущий релиз добавит migrations, перед rollback нужно зафиксировать database backup ID и restore point.
