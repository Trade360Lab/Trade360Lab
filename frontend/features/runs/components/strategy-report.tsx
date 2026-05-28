"use client";

import { AlertCircle } from "lucide-react";
import type { Run } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { SurfaceCard } from "@/components/shared/surface-card";
import { MetricCard } from "@/features/runs/components/metric-card";
import {
  buildStrategyReportMetrics,
  diagnosticsStatusLabel,
  diagnosticsWarningLabel,
  formatDiagnosticsNumber,
  formatDiagnosticsPercent,
  warningTone,
} from "@/lib/run-diagnostics";

type StrategyReportProps = {
  run: Run;
};

export function StrategyReport({ run }: StrategyReportProps) {
  const diagnostics = run.diagnostics;
  const metrics = buildStrategyReportMetrics(run);

  return (
    <SurfaceCard title="Strategy Report" subtitle={diagnostics?.diagnosticsSummary ?? "Diagnostics are not available for this run."}>
      <div className="space-y-4 p-4">
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-6">
          {metrics.map((metric) => (
            <MetricCard
              key={metric.label}
              label={metric.label}
              value={metric.value}
              tone={metric.tone}
            />
          ))}
        </div>

        {!diagnostics ? (
          <div className="flex items-start gap-2 rounded-md border border-border bg-panel-subtle p-3 text-xs text-muted-foreground">
            <AlertCircle className="mt-0.5 h-4 w-4 text-status-warning" />
            <span>Old or incomplete run result. Strategy diagnostics will appear after a new backtest completes.</span>
          </div>
        ) : (
          <>
            <div className="flex flex-wrap gap-2">
              {diagnostics.warnings.length === 0 ? (
                <Badge variant="secondary">No warnings</Badge>
              ) : (
                diagnostics.warnings.map((warning) => (
                  <Badge key={warning.code} variant={warningTone(warning)}>
                    {diagnosticsWarningLabel(warning)}
                  </Badge>
                ))
              )}
            </div>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
              <div className="space-y-3">
                <div className="text-xs font-medium uppercase text-muted-foreground">Drawdown / Equity</div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="rounded-md border border-border bg-panel-subtle p-3">
                    <div className="text-muted-foreground">Max drawdown</div>
                    <div className="mt-1 text-sm font-medium text-loss">
                      {formatDiagnosticsNumber(diagnostics.risk.maxDrawdown)}
                    </div>
                  </div>
                  <div className="rounded-md border border-border bg-panel-subtle p-3">
                    <div className="text-muted-foreground">Drawdown %</div>
                    <div className="mt-1 text-sm font-medium text-loss">
                      {formatDiagnosticsPercent(diagnostics.risk.maxDrawdownPct)}
                    </div>
                  </div>
                  <div className="rounded-md border border-border bg-panel-subtle p-3">
                    <div className="text-muted-foreground">Drawdown start</div>
                    <div className="mt-1 font-mono text-[11px] text-foreground">
                      {diagnostics.risk.drawdownStart ?? "n/a"}
                    </div>
                  </div>
                  <div className="rounded-md border border-border bg-panel-subtle p-3">
                    <div className="text-muted-foreground">Recovery bars</div>
                    <div className="mt-1 text-sm font-medium text-foreground">
                      {diagnostics.risk.recoveryBars ?? "n/a"}
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                <div className="text-xs font-medium uppercase text-muted-foreground">Trade Distribution</div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <Metric label="Best trade" value={formatDiagnosticsNumber(diagnostics.trades.bestTrade)} tone="profit" />
                  <Metric label="Worst trade" value={formatDiagnosticsNumber(diagnostics.trades.worstTrade)} tone="loss" />
                  <Metric label="Average win" value={formatDiagnosticsNumber(diagnostics.trades.averageWin)} tone="profit" />
                  <Metric label="Average loss" value={formatDiagnosticsNumber(diagnostics.trades.averageLoss)} tone="loss" />
                  <Metric label="Win streak" value={String(diagnostics.trades.longestWinStreak)} />
                  <Metric label="Loss streak" value={String(diagnostics.trades.longestLossStreak)} />
                </div>
              </div>
            </div>

            <div>
              <div className="mb-2 text-xs font-medium uppercase text-muted-foreground">Stability Segments</div>
              {diagnostics.stability.segments.length === 0 ? (
                <div className="rounded-md border border-border bg-panel-subtle p-3 text-xs text-muted-foreground">
                  No stability segments for this run.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-md border border-border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Segment</TableHead>
                        <TableHead>Period</TableHead>
                        <TableHead>PnL</TableHead>
                        <TableHead>Trades</TableHead>
                        <TableHead>Max DD</TableHead>
                        <TableHead>Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {diagnostics.stability.segments.map((segment) => (
                        <TableRow key={segment.segmentIndex}>
                          <TableCell className="text-xs">{segment.segmentIndex}</TableCell>
                          <TableCell className="font-mono text-[11px] text-muted-foreground">
                            {(segment.from ?? "n/a") + " -> " + (segment.to ?? "n/a")}
                          </TableCell>
                          <TableCell className={segment.pnl >= 0 ? "text-xs text-profit" : "text-xs text-loss"}>
                            {formatDiagnosticsNumber(segment.pnl)}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">{segment.tradeCount}</TableCell>
                          <TableCell className="text-xs text-loss">
                            {formatDiagnosticsNumber(segment.maxDrawdown)}
                          </TableCell>
                          <TableCell>
                            <Badge variant="secondary">{diagnosticsStatusLabel(segment.status)}</Badge>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </SurfaceCard>
  );
}

function Metric({ label, value, tone }: { label: string; value: string; tone?: "profit" | "loss" }) {
  return (
    <div className="rounded-md border border-border bg-panel-subtle p-3">
      <div className="text-[11px] uppercase text-muted-foreground">{label}</div>
      <div className={tone === "loss" ? "mt-1 text-sm font-medium text-loss" : tone === "profit" ? "mt-1 text-sm font-medium text-profit" : "mt-1 text-sm font-medium text-foreground"}>
        {value}
      </div>
    </div>
  );
}
