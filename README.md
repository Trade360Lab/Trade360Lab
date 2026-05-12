<p align="center">
  <img src="./frontend/public/Logo.png" alt="TradeLab Logo" />
</p>
<h1 align="center">Trade360Lab</h1>

Это монорепозиторий платформы для исследования, подготовки данных, запуска и сравнения торговых сценариев. Основной интерфейс находится во `frontend` и построен на Next.js: в нём собраны рабочее пространство, экран данных, бэктесты, карточки запусков и сравнение результатов. Папка `backend` содержит Java API и Python parser/backtesting сервис. В репозитории также есть `docs` с проектной документацией.

<h2 align="center">Архитектура</h2>

```mermaid
flowchart TB
    U[Пользователь] --> UI[Next.js App Router UI]

    subgraph Frontend["frontend/"]
        direction TB
        UI --> Shell[Dashboard shell]
        UI --> Auth[Login / Register]
        UI --> Workspace[Workspace pages]
        UI --> Proxy[Next.js API routes / proxy]
        Shell --> Components[Shared UI components]
        Workspace --> DataScreens[Data, Backtests, Strategies, Paper, Live, Bots]
    end

    subgraph Backend["backend/"]
        direction TB
        Java[Spring Boot Java API]
        Python[FastAPI Python parser / engine]
        DB[(PostgreSQL)]
    end

    subgraph JavaLayer["Java API layer"]
        direction TB
        Java --> Controllers[Controllers]
        Java --> Services[Services]
        Services --> DatasetApi[Dataset API]
        Services --> StrategyApi[Strategy management]
        Services --> RunApi[Run control]
        Services --> TradingApi[Paper / Live trading API]
    end

    subgraph PythonLayer["Python engine layer"]
        direction TB
        Python --> Parser[Market data parser]
        Python --> Runner[Strategy runner]
        Python --> Backtesting[Backtesting]
        Python --> Indicators[Indicators]
        Python --> ExchangeAdapters[Exchange adapters]
    end

    Proxy --> Java
    Java <--> DB
    Java <--> Python
    Python <--> DB
```

<h2 align="center">Текущая структура проекта</h2>

```text
Trade360Lab/
|-- frontend/               # Next.js приложение (UI + API proxy)
|   |-- app/
|   |-- components/
|   |-- features/
|   |-- lib/
|   `-- public/
|-- backend/
|   |-- java/               # Spring Boot API
|   `-- python/             # FastAPI parser/import service
|-- docs/                   # Проектная документация
|-- .github/workflows/      # CI пайплайн
`-- docker-compose.yml      # Оркестрация всего стека
```

<h2 align="center">Процесс запуска бэктеста</h2>

```mermaid
sequenceDiagram
    actor Trader as Трейдер
    participant UI as Frontend UI
    participant API as Next.js API proxy
    participant Java as Java API
    participant Py as Python engine
    participant DB as PostgreSQL

    Trader->>UI: Выбирает датасет, стратегию и параметры
    UI->>API: POST /api/runs
    API->>Java: Создать запуск
    Java->>DB: Сохранить run со статусом queued
    Java->>Py: Передать конфигурацию бэктеста
    Py->>DB: Прочитать свечи и версию стратегии
    Py-->>Java: Вернуть метрики, сделки и артефакты
    Java->>DB: Обновить run, результаты и статус
    UI->>API: GET /api/runs/[id]
    API->>Java: Запросить актуальное состояние
    Java-->>API: Run details
    API-->>UI: Результаты для карточки запуска
```


<h2 align="center">Быстрый старт</h2>

<h3 align="center">Вариант A: весь стек в Docker (рекомендуется)</h3>

```bash
docker compose up --build
```

Сервисы:
- Frontend: `http://localhost:3000` (или `${FRONTEND_HOST_PORT}`)
- Java API: `http://localhost:18080` (или `${JAVA_API_HOST_PORT}`)
- Python parser: `http://localhost:18000` (или `${PYTHON_PARSER_HOST_PORT}`)
- PostgreSQL: `localhost:55432` (или `${POSTGRES_HOST_PORT}`, если переопределён)

<h3 align="center">Вариант B: локальная разработка</h3>

1. Фронтенд
```bash
cd frontend
npm install
npm run dev
```

2. Python parser
```bash
cd backend/python
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn parser.main:app --host 0.0.0.0 --port 8000
```

3. Java API
```bash
cd backend/java
mvn spring-boot:run
```

<h2 align="center">Подробная документация</h2>

- Фронтенд: [`frontend/README.md`](./frontend/README.md)
- Обзор бэкенда: [`backend/README.md`](./backend/README.md)
- Java API: [`backend/java/README.md`](./backend/java/README.md)
- Python parser: [`backend/python/README.md`](./backend/python/README.md)
- Release checklist: [`docs/release-checklist.md`](./docs/release-checklist.md)

--- 
<p align="center">
  <img src="./frontend/public/png2.jpg" alt="TradeLab Logo" />
</p>

---

<p align="center">
  Copyright (C) 2026 AlexToday111 <br/>
  This project is licensed under the GNU General Public License v3.0
</p>
