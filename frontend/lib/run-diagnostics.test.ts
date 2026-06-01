import {
  diagnosticsStatusLabel,
  diagnosticsWarningLabel,
  formatDiagnosticsValue,
  normalizeRunDiagnostics,
} from "@/lib/run-diagnostics";

describe("run diagnostics helpers", () => {
  it("normalizes a backend diagnostics payload", () => {
    const diagnostics = normalizeRunDiagnostics({
      diagnosticsStatus: "mixed",
      diagnosticsSummary: "Backtest produced too few trades to evaluate stability.",
      totalPnL: "9.5",
      risk: {
        maxDrawdownPct: 1.2,
      },
      trades: {
        tradeCount: 4,
        winRate: "50",
        profitFactor: 1.4,
      },
      stability: {
        status: "mixed",
        segments: [
          {
            segmentIndex: 1,
            from: "2024-01-01T00:00:00Z",
            to: "2024-01-01T01:00:00Z",
            pnl: 9.5,
            tradeCount: 4,
            maxDrawdown: 2.5,
            status: "mixed",
          },
        ],
      },
      warnings: [{ code: "LOW_TRADE_COUNT", message: "Too few trades", severity: "warning" }],
    });

    expect(diagnostics?.diagnosticsStatus).toBe("mixed");
    expect(diagnostics?.totalPnL).toBe(9.5);
    expect(diagnostics?.trades.tradeCount).toBe(4);
    expect(diagnostics?.stability.segments[0].status).toBe("mixed");
    expect(diagnostics?.warnings[0].code).toBe("LOW_TRADE_COUNT");
  });

  it("returns null for missing diagnostics payloads", () => {
    expect(normalizeRunDiagnostics(null)).toBeNull();
    expect(normalizeRunDiagnostics({ risk: {} })).toBeNull();
  });

  it("formats labels and values for report rendering", () => {
    expect(formatDiagnosticsValue(1.2345, { suffix: "%", digits: 1 })).toBe("1.2%");
    expect(formatDiagnosticsValue(null)).toBe("N/A");
    expect(diagnosticsStatusLabel("fragile")).toBe("Fragile");
    expect(diagnosticsWarningLabel("HIGH_DRAWDOWN")).toBe("High drawdown");
  });
});
