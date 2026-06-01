import type {
  RunDiagnostics,
  RunDiagnosticsRisk,
  RunDiagnosticsStability,
  RunDiagnosticsTrades,
  RunDiagnosticsWarning,
} from "@/lib/types";

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function toNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function toInteger(value: unknown, fallback = 0) {
  const numberValue = toNumber(value);
  return numberValue === null ? fallback : Math.round(numberValue);
}

function toStringOrNull(value: unknown) {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}

function normalizeWarnings(value: unknown): RunDiagnosticsWarning[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter(isRecord)
    .map((warning) => ({
      code: toStringOrNull(warning.code) ?? "UNKNOWN",
      message: toStringOrNull(warning.message) ?? "Diagnostics warning",
      severity: toStringOrNull(warning.severity) ?? "warning",
    }));
}

function normalizeRisk(value: unknown): RunDiagnosticsRisk {
  const risk = isRecord(value) ? value : {};
  return {
    maxDrawdown: toNumber(risk.maxDrawdown),
    maxDrawdownPct: toNumber(risk.maxDrawdownPct),
    drawdownStart: toStringOrNull(risk.drawdownStart),
    drawdownEnd: toStringOrNull(risk.drawdownEnd),
    recoveryBars: toNumber(risk.recoveryBars),
  };
}

function normalizeTrades(value: unknown): RunDiagnosticsTrades {
  const trades = isRecord(value) ? value : {};
  return {
    tradeCount: toInteger(trades.tradeCount),
    winningTrades: toInteger(trades.winningTrades),
    losingTrades: toInteger(trades.losingTrades),
    winRate: toNumber(trades.winRate),
    profitFactor: toNumber(trades.profitFactor),
    averageWin: toNumber(trades.averageWin),
    averageLoss: toNumber(trades.averageLoss),
    bestTrade: toNumber(trades.bestTrade),
    worstTrade: toNumber(trades.worstTrade),
    longestWinStreak: toInteger(trades.longestWinStreak),
    longestLossStreak: toInteger(trades.longestLossStreak),
    averageTradePnl: toNumber(trades.averageTradePnl),
    medianTradePnl: toNumber(trades.medianTradePnl),
    flatTrades: toInteger(trades.flatTrades),
  };
}

function normalizeStability(value: unknown): RunDiagnosticsStability {
  const stability = isRecord(value) ? value : {};
  const segments = Array.isArray(stability.segments) ? stability.segments : [];
  return {
    status: toStringOrNull(stability.status) ?? "unavailable",
    segments: segments.filter(isRecord).map((segment, index) => ({
      segmentIndex: toInteger(segment.segmentIndex, index + 1),
      from: toStringOrNull(segment.from),
      to: toStringOrNull(segment.to),
      pnl: toNumber(segment.pnl),
      tradeCount: toInteger(segment.tradeCount),
      maxDrawdown: toNumber(segment.maxDrawdown),
      status: toStringOrNull(segment.status) ?? "unavailable",
    })),
  };
}

export function normalizeRunDiagnostics(payload: unknown): RunDiagnostics | null {
  if (!isRecord(payload)) {
    return null;
  }

  const status = toStringOrNull(payload.diagnosticsStatus);
  const summary = toStringOrNull(payload.diagnosticsSummary);
  if (!status || !summary) {
    return null;
  }

  return {
    diagnosticsStatus: status,
    diagnosticsSummary: summary,
    totalPnL: toNumber(payload.totalPnL),
    totalReturnPct: toNumber(payload.totalReturnPct),
    risk: normalizeRisk(payload.risk),
    trades: normalizeTrades(payload.trades),
    stability: normalizeStability(payload.stability),
    warnings: normalizeWarnings(payload.warnings),
  };
}

export function formatDiagnosticsValue(
  value: number | null | undefined,
  options?: { suffix?: string; digits?: number; fallback?: string }
) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return options?.fallback ?? "N/A";
  }
  const digits = options?.digits ?? 2;
  return `${value.toFixed(digits)}${options?.suffix ?? ""}`;
}

export function diagnosticsStatusLabel(status: string | null | undefined) {
  switch ((status ?? "").toLowerCase()) {
    case "healthy":
      return "Healthy";
    case "mixed":
      return "Mixed";
    case "fragile":
      return "Fragile";
    case "strong":
      return "Strong";
    case "weak":
      return "Weak";
    case "no_trades":
      return "No trades";
    case "unavailable":
      return "Unavailable";
    default:
      return status ?? "Unavailable";
  }
}

export function diagnosticsWarningLabel(code: string) {
  switch (code) {
    case "LOW_TRADE_COUNT":
      return "Low sample size";
    case "HIGH_DRAWDOWN":
      return "High drawdown";
    case "NO_TRADES":
      return "No trades";
    case "UNSTABLE_SEGMENTS":
      return "Unstable result";
    case "NEGATIVE_EXPECTANCY":
      return "Negative expectancy";
    case "ALL_TRADES_LOSING":
      return "All trades losing";
    case "PROFIT_FACTOR_UNAVAILABLE":
      return "Profit factor unavailable";
    default:
      return code.replaceAll("_", " ").toLowerCase();
  }
}
