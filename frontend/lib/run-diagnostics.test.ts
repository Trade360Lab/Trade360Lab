import { buildStrategyReportMetrics, diagnosticsStatusLabel, diagnosticsWarningsCount, diagnosticsWarningLabel } from "@/lib/run-diagnostics";
import type { Run, RunDiagnostics, RunDiagnosticsWarning } from "@/lib/types";

function run(overrides: Partial<Run>): Run {
  return {
    id: "run-1",
    strategy: "atlas.py",
    datasetVersion: "dataset-v1",
    period: "2024-01",
    timeframe: "1h",
    params: {
      fees: "0.1%",
      slippage: "0.05%",
      execution: "market",
      riskPerTrade: "1%",
      maxExposure: "10%",
      symbols: ["BTCUSDT"],
      timeframe: "1h",
      period: "2024-01",
    },
    metrics: {
      pnl: 12.3,
      sharpe: 1.1,
      maxDrawdown: -8.4,
      trades: 42,
      winrate: 52.4,
      avgTrade: 0.2,
      feesImpact: -1.1,
    },
    status: "done",
    artifacts: [],
    createdAt: "2024-01-01T00:00:00Z",
    commit: "abc123",
    config: "default",
    tags: [],
    diff: {
      code: false,
      data: false,
      config: false,
    },
    ...overrides,
  };
}

describe("run diagnostics helpers", () => {
  it("builds Strategy Report metrics from diagnostics", () => {
    const metrics = buildStrategyReportMetrics(run({
      diagnostics: {
        diagnosticsStatus: "mixed",
        diagnosticsSummary: "summary",
        risk: {
          totalPnl: 120,
          totalReturnPct: 12,
          maxDrawdown: 80,
          maxDrawdownPct: 8,
        },
        trades: {
          tradeCount: 42,
          winningTrades: 22,
          losingTrades: 20,
          winRate: 52.38,
          profitFactor: 1.34,
          averageWin: 18,
          averageLoss: -14,
          bestTrade: 104,
          worstTrade: -76,
          longestWinStreak: 5,
          longestLossStreak: 4,
        },
        stability: {
          status: "mixed",
          segments: [],
        },
        warnings: [],
      },
    }));

    expect(metrics.map((metric) => metric.value)).toEqual(["120", "8.00%", "52.38%", "1.34", "42", "Mixed"]);
  });

  it("builds missing-state metrics without diagnostics", () => {
    const metrics = buildStrategyReportMetrics(run({ diagnostics: null }));

    expect(metrics.find((metric) => metric.label === "Profit Factor")?.value).toBe("n/a");
    expect(metrics.find((metric) => metric.label === "Stability")?.value).toBe("Unavailable");
  });

  it("maps compare status and warning labels", () => {
    const warning: RunDiagnosticsWarning = {
      code: "LOW_TRADE_COUNT",
      severity: "medium",
      message: "small sample",
    };

    expect(diagnosticsStatusLabel("fragile")).toBe("Fragile");
    expect(diagnosticsWarningLabel(warning)).toBe("Low sample size");
    expect(diagnosticsWarningsCount({ warnings: [warning] } as RunDiagnostics)).toBe(1);
  });
});
