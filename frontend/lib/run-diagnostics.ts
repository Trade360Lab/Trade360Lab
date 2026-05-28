import type { Run, RunDiagnostics, RunDiagnosticsWarning } from "@/lib/types";

export type StrategyReportMetric = {
  label: string;
  value: string;
  tone?: "profit" | "loss";
};

const warningLabels: Record<string, string> = {
  LOW_TRADE_COUNT: "Low sample size",
  HIGH_DRAWDOWN: "High drawdown",
  NEGATIVE_EXPECTANCY: "Negative expectancy",
  UNSTABLE_SEGMENTS: "Unstable result",
  NO_TRADES: "No trades",
  ALL_TRADES_LOSING: "All trades losing",
  PROFIT_FACTOR_UNAVAILABLE: "Profit factor unavailable",
};

const statusLabels: Record<string, string> = {
  healthy: "Healthy",
  mixed: "Mixed",
  fragile: "Fragile",
  unavailable: "Unavailable",
  strong: "Strong",
  weak: "Weak",
  no_trades: "No trades",
};

export function formatDiagnosticsNumber(value: number | null | undefined, suffix = "") {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "n/a";
  }
  return `${value.toFixed(Math.abs(value) >= 100 ? 0 : 2)}${suffix}`;
}

export function formatDiagnosticsPercent(value: number | null | undefined) {
  return formatDiagnosticsNumber(value, "%");
}

export function diagnosticsStatusLabel(status: string | null | undefined) {
  if (!status) {
    return "Unavailable";
  }
  return statusLabels[status] ?? status.replaceAll("_", " ");
}

export function diagnosticsWarningLabel(warning: RunDiagnosticsWarning) {
  return warningLabels[warning.code] ?? warning.code.replaceAll("_", " ");
}

export function warningTone(warning: RunDiagnosticsWarning) {
  if (warning.severity === "high") {
    return "destructive" as const;
  }
  return "secondary" as const;
}

export function buildStrategyReportMetrics(run: Pick<Run, "metrics" | "diagnostics">): StrategyReportMetric[] {
  const diagnostics = run.diagnostics;
  if (!diagnostics) {
    return [
      { label: "Total PnL", value: formatDiagnosticsPercent(run.metrics.pnl), tone: run.metrics.pnl >= 0 ? "profit" : "loss" },
      { label: "Max Drawdown", value: formatDiagnosticsPercent(run.metrics.maxDrawdown), tone: "loss" },
      { label: "Win Rate", value: formatDiagnosticsPercent(run.metrics.winrate) },
      { label: "Profit Factor", value: "n/a" },
      { label: "Trade Count", value: String(run.metrics.trades) },
      { label: "Stability", value: "Unavailable" },
    ];
  }

  return [
    {
      label: "Total PnL",
      value: formatDiagnosticsNumber(diagnostics.risk.totalPnl),
      tone: (diagnostics.risk.totalPnl ?? 0) >= 0 ? "profit" : "loss",
    },
    {
      label: "Max Drawdown",
      value: formatDiagnosticsPercent(diagnostics.risk.maxDrawdownPct),
      tone: "loss",
    },
    {
      label: "Win Rate",
      value: formatDiagnosticsPercent(diagnostics.trades.winRate),
    },
    {
      label: "Profit Factor",
      value: formatDiagnosticsNumber(diagnostics.trades.profitFactor),
    },
    {
      label: "Trade Count",
      value: String(diagnostics.trades.tradeCount),
    },
    {
      label: "Stability",
      value: diagnosticsStatusLabel(diagnostics.stability.status),
    },
  ];
}

export function diagnosticsWarningsCount(diagnostics: RunDiagnostics | null | undefined) {
  return diagnostics?.warnings.length ?? 0;
}
