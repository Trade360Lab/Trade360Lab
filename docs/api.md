<h1 align="center">REST API</h1>

<h2 align="center">POST `/backtests`</h2>

Создает запуск и синхронно выполняет бэктест.

<h3 align="center">Пример запроса</h3>

```json
{
  "strategyId": 42,
  "exchange": "binance",
  "symbol": "BTCUSDT",
  "interval": "1h",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-01-03T00:00:00Z",
  "params": {
    "fastPeriod": 10,
    "slowPeriod": 21
  },
  "initialCash": 10000.0,
  "feeRate": 0.001,
  "slippageBps": 5.0,
  "strictData": true
}
```

<h3 align="center">Пример ответа</h3>

```json
{
  "runId": 101
}
```

<h2 align="center">GET `/backtests/{id}`</h2>

Возвращает состояние запуска.

<h3 align="center">Пример ответа</h3>

```json
{
  "runId": 101,
  "strategyId": 42,
  "status": "COMPLETED",
  "exchange": "binance",
  "symbol": "BTCUSDT",
  "interval": "1h",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-01-03T00:00:00Z",
  "params": {
    "fastPeriod": 10,
    "slowPeriod": 21
  },
  "summary": {
    "profit": 12.5,
    "sharpe": 1.3
  },
  "errorMessage": null,
  "createdAt": "2024-01-01T00:00:00Z",
  "startedAt": "2024-01-01T00:00:01Z",
  "finishedAt": "2024-01-01T00:00:09Z"
}
```

<h2 align="center">GET `/backtests/{id}/trades`</h2>

Возвращает сделки запуска.

<h3 align="center">Пример ответа</h3>

```json
[
  {
    "entry_time": "2024-01-01T00:00:00Z",
    "exit_time": "2024-01-01T01:00:00Z",
    "entry_price": 100.0,
    "exit_price": 109.5,
    "qty": 1.0,
    "pnl": 9.5,
    "fee": 0.2
  }
]
```

<h2 align="center">GET `/backtests/{id}/equity`</h2>

Возвращает кривую капитала.

<h3 align="center">Пример ответа</h3>

```json
[
  {
    "timestamp": "2024-01-01T01:00:00Z",
    "equity": 10009.5
  }
]
```

<h2 align="center">API масштабируемых запусков</h2>

<h3 align="center">POST `/api/runs`</h3>

Создает `Run`, immutable snapshot и `ExecutionJob` со статусом `QUEUED`.
`strategyVersionId` и `parameterPresetId` опциональны. Если `strategyVersionId` не передан, backend использует latest version стратегии для совместимости и сохраняет выбранную version в `runs.strategy_version_id` и `run_snapshots.strategy_version_id`.

```json
{
  "strategyId": 42,
  "strategyVersionId": 101,
  "parameterPresetId": 7,
  "exchange": "binance",
  "symbol": "BTCUSDT",
  "interval": "1h",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-01-03T00:00:00Z",
  "params": {
    "fastPeriod": 10
  }
}
```

<h3 align="center">GET `/api/runs/{id}/execution`</h3>

Возвращает latest execution job текущего пользователя для запуска.

<h3 align="center">POST `/api/runs/{id}/retry`</h3>

Повторно ставит failed job в очередь, если лимит `maxAttempts` не исчерпан. Canceled jobs не retry-ятся.

<h3 align="center">POST `/api/runs/{id}/cancel`</h3>

Отменяет queued job сразу. Для running job выставляет `cancelRequested=true`; Python interruption будет добавлен позже.

<h3 align="center">GET `/api/runs/{id}/result`</h3>

Возвращает результат запуска, сделки, equity curve, artifacts metadata и optional diagnostics. Старые runs могут не иметь diagnostics, поэтому clients должны поддерживать `diagnostics=null` или отсутствие поля.

Пример diagnostics payload:

```json
{
  "diagnosticsStatus": "mixed",
  "diagnosticsSummary": "Backtest produced too few trades to evaluate stability.",
  "risk": {
    "maxDrawdown": 123.45,
    "maxDrawdownPct": 8.2,
    "drawdownStart": "2024-01-01T00:00:00Z",
    "drawdownEnd": "2024-01-05T00:00:00Z",
    "recoveryBars": 12
  },
  "trades": {
    "tradeCount": 42,
    "winningTrades": 22,
    "losingTrades": 20,
    "winRate": 52.38,
    "profitFactor": 1.34,
    "averageWin": 18.4,
    "averageLoss": -14.1,
    "bestTrade": 104.2,
    "worstTrade": -76.5,
    "longestWinStreak": 5,
    "longestLossStreak": 4
  },
  "stability": {
    "status": "mixed",
    "segments": []
  },
  "warnings": []
}
```

Diagnostics являются research artifact для alpha validation. Они не являются trading signal и не подтверждают production trading readiness.

<h3 align="center">GET `/api/execution-jobs`</h3>

Возвращает execution jobs текущего пользователя.

<h3 align="center">GET `/api/execution-jobs/{id}`</h3>

Возвращает execution job по id с ownership-проверкой.

<h2 align="center">API управления стратегиями</h2>

Все endpoints требуют JWT-auth, кроме внутренних Python endpoints. User-owned resources возвращаются только текущему пользователю.

<h3 align="center">POST `/api/strategies`</h3>

Создает draft strategy registry record без source version.

<h3 align="center">GET `/api/strategies`</h3>

Возвращает стратегии текущего пользователя.

<h3 align="center">GET `/api/strategies/{id}`</h3>

Возвращает одну owned strategy.

<h3 align="center">PATCH `/api/strategies/{id}`</h3>

Обновляет editable metadata: `name`, `description`, `strategyType`, `lifecycleStatus`, `metadata`, `tags`.

<h3 align="center">POST `/api/strategies/{id}/archive`</h3>

Переводит strategy lifecycle в `ARCHIVED`.

<h3 align="center">POST `/api/strategies/upload`</h3>

Compatibility upload flow. Создает strategy registry record и initial immutable version из `.py` файла.

<h3 align="center">POST `/api/strategies/{id}/versions`</h3>

Загружает новый `.py` файл как immutable strategy version. Version получает checksum, file metadata и persisted validation result.

<h3 align="center">GET `/api/strategies/{id}/versions`</h3>

Возвращает version history стратегии.

<h3 align="center">GET `/api/strategy-versions/{versionId}`</h3>

Возвращает одну owned strategy version.

<h3 align="center">POST `/api/strategy-versions/{versionId}/validate`</h3>

Повторно запускает validation contract и сохраняет validation report.

<h3 align="center">POST `/api/strategy-versions/{versionId}/activate`</h3>

Активирует только `VALID` или `WARNING` version. `INVALID` и `PENDING` versions не активируются.

<h3 align="center">GET `/api/strategy-templates`</h3>

Возвращает system-owned starter templates.

<h3 align="center">GET `/api/strategy-templates/{id}`</h3>

Возвращает один template.

<h3 align="center">POST `/api/strategies/{id}/presets`</h3>

Создает owner-scoped parameter preset.

```json
{
  "name": "BTC 1h default",
  "presetPayload": {
    "fastPeriod": 10,
    "slowPeriod": 21
  }
}
```

<h3 align="center">GET `/api/strategies/{id}/presets`</h3>

Возвращает presets owned user для strategy.

<h3 align="center">PATCH `/api/strategy-presets/{presetId}`</h3>

Обновляет preset name/payload.

<h3 align="center">DELETE `/api/strategy-presets/{presetId}`</h3>

Удаляет owned preset.

<h2 align="center">API артефактов запусков</h2>

<h3 align="center">GET `/api/runs/{id}/artifacts`</h3>

Возвращает metadata артефактов запуска, доступных текущему пользователю. Для runs с diagnostics успешный запуск создает `strategy-report-{runId}.json`.

<h3 align="center">GET `/api/runs/{id}/artifacts/{artifactId}`</h3>

Возвращает metadata и JSON payload конкретного артефакта.

<h3 align="center">GET `/api/runs/{id}/artifacts/{artifactId}/download`</h3>

Возвращает содержимое артефакта как downloadable file.

<h2 align="center">API платформы датасетов</h2>

<h3 align="center">GET `/api/datasets/{id}`</h3>

Возвращает dataset payload, latest snapshot и latest quality report.

<h3 align="center">GET `/api/datasets/{id}/versions`</h3>

Возвращает snapshot/version history dataset.

<h3 align="center">GET `/api/datasets/{id}/quality`</h3>

Возвращает сохраненные quality reports dataset.

<h3 align="center">GET `/api/dataset-snapshots/{snapshotId}`</h3>

Возвращает metadata конкретного dataset snapshot с ownership-проверкой через parent dataset.

<h2 align="center">API Paper Trading</h2>

Все endpoints требуют JWT-auth и возвращают только ресурсы текущего пользователя.

<h3 align="center">POST `/api/paper/sessions`</h3>

Создает paper trading session со статусом `CREATED`.

```json
{
  "name": "BTC paper",
  "exchange": "binance",
  "symbol": "BTCUSDT",
  "timeframe": "1h",
  "initialBalance": 10000,
  "baseCurrency": "BTC",
  "quoteCurrency": "USDT"
}
```

<h3 align="center">GET `/api/paper/sessions`</h3>

Возвращает sessions текущего пользователя.

<h3 align="center">GET `/api/paper/sessions/{id}`</h3>

Возвращает одну owned session.

<h3 align="center">POST `/api/paper/sessions/{id}/start`</h3>

Переводит session в `RUNNING`.

<h3 align="center">POST `/api/paper/sessions/{id}/pause`</h3>

Переводит running session в `PAUSED`.

<h3 align="center">POST `/api/paper/sessions/{id}/stop`</h3>

Переводит session в `STOPPED`.

<h3 align="center">POST `/api/paper/sessions/{id}/orders`</h3>

Создает simulated order. Market orders исполняются сразу по latest stored candle close. Limit orders принимаются и исполняются в момент submission только если latest close пересекает limit.

```json
{
  "side": "BUY",
  "type": "MARKET",
  "quantity": 0.01
}
```

```json
{
  "side": "BUY",
  "type": "LIMIT",
  "quantity": 0.01,
  "price": 60000
}
```

Rejected orders сохраняются со `status=REJECTED` и `rejectedReason`; fill не создается.

<h3 align="center">GET `/api/paper/sessions/{id}/orders`</h3>

Возвращает orders для owned session.

<h3 align="center">GET `/api/paper/orders/{orderId}`</h3>

Возвращает один owned paper order.

<h3 align="center">POST `/api/paper/orders/{orderId}/cancel`</h3>

Отменяет paper order в статусе `NEW` или `ACCEPTED`.

<h3 align="center">GET `/api/paper/sessions/{id}/positions`</h3>

Возвращает paper positions для owned session.

<h3 align="center">GET `/api/paper/sessions/{id}/fills`</h3>

Возвращает simulated fills для owned session.

<h3 align="center">GET `/api/paper/sessions/{id}/summary`</h3>

Возвращает balance, PnL, equity, order count, fill count и open position count для owned session.

Ни один endpoint в этом релизе не размещает real exchange orders.

<h2 align="center">API Live Trading</h2>

Все endpoints требуют JWT-auth и возвращают только ресурсы текущего пользователя.

<h3 align="center">POST `/api/live/credentials`</h3>

Сохраняет encrypted live exchange credentials. API key и secret никогда не возвращаются в responses.

```json
{
  "exchange": "binance",
  "apiKey": "******",
  "apiSecret": "******",
  "active": true
}
```

<h3 align="center">GET `/api/live/credentials/status`</h3>

Возвращает masked credential status: `id`, `exchange`, `keyReference`, `active`, `createdAt`, `updatedAt`.

<h3 align="center">POST `/api/live/sessions`</h3>

Создает owner-scoped guarded live trading session с явными risk limits.

```json
{
  "name": "BTC live guarded",
  "exchange": "binance",
  "symbol": "BTCUSDT",
  "baseCurrency": "BTC",
  "quoteCurrency": "USDT",
  "maxOrderNotional": 100,
  "maxPositionNotional": 500,
  "maxDailyNotional": 1000,
  "symbolWhitelist": "BTCUSDT"
}
```

<h3 align="center">GET `/api/live/sessions`</h3>

Возвращает live sessions для authenticated user.

<h3 align="center">POST `/api/live/sessions/{id}/enable`</h3>

Включает live session только при наличии active credentials.

<h3 align="center">POST `/api/live/sessions/{id}/disable`</h3>

Отключает live session и блокирует размещение новых live orders через эту session.

<h3 align="center">POST `/api/live/orders`</h3>

Создает live order. Mandatory risk checks выполняются до adapter submission. Rejected orders сохраняются и никогда не доходят до exchange.

```json
{
  "sessionId": 1,
  "strategyId": 12,
  "strategyVersionId": 44,
  "side": "BUY",
  "type": "MARKET",
  "quantity": 0.001,
  "sourceRunId": 55
}
```

<h3 align="center">GET `/api/live/orders`</h3>

Возвращает live orders текущего пользователя.

<h3 align="center">GET `/api/live/orders/{id}`</h3>

Возвращает один owned live order.

<h3 align="center">POST `/api/live/orders/{id}/cancel`</h3>

Отменяет open live order. Ownership проверяется до adapter cancellation.

<h3 align="center">GET `/api/live/positions`</h3>

Возвращает local live position state.

<h3 align="center">POST `/api/live/positions/sync`</h3>

Синхронизирует positions из configured exchange adapters, где это поддержано.

<h3 align="center">GET `/api/live/balances`</h3>

Возвращает adapter balance snapshots, где это поддержано.

<h3 align="center">GET `/api/live/risk/status`</h3>

Возвращает состояния kill switch и circuit breaker.

<h3 align="center">GET `/api/live/risk/events`</h3>

Возвращает последние live risk и safety events.

<h3 align="center">POST `/api/live/kill-switch/activate`</h3>

Активирует manual emergency stop и блокирует новые live orders.

```json
{
  "reason": "Manual emergency stop",
  "cancelOpenOrders": false
}
```

<h3 align="center">POST `/api/live/kill-switch/reset`</h3>

Вручную сбрасывает kill switch.

<h3 align="center">POST `/api/live/circuit-breakers/reset`</h3>

Вручную сбрасывает circuit breaker state текущего пользователя.

<h3 align="center">GET `/api/live/exchange/health?exchange=binance`</h3>

Проверяет adapter connectivity, наличие/валидность credentials и то, включен ли real order submission.

<h2 align="center">Ошибки</h2>

Все ошибки возвращаются в JSON:

```json
{
  "timestamp": "2024-01-01T00:00:05Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Field 'from' must be before 'to'",
  "path": "/backtests"
}
```
