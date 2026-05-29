"use client";

import { useMemo, useState } from "react";
import { ChartCard } from "@/components/shared/chart-card";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { SurfaceCard } from "@/components/shared/surface-card";
import { Checkbox } from "@/components/ui/checkbox";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { useRuns } from "@/features/runs/store/run-store";
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid } from "recharts";
import { equityCurve } from "@/lib/demo-data/charts";
import {
  diagnosticsStatusLabel,
  formatDiagnosticsValue,
} from "@/lib/run-diagnostics";

const lineColors = [
  "hsl(var(--chart-1))",
  "hsl(var(--chart-2))",
  "hsl(var(--chart-3))",
  "hsl(var(--chart-4))",
  "hsl(var(--chart-5))",
];

export default function CompareRunsPage() {
  const { runs } = useRuns();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const toggleRun = (id: string) => {
    setSelectedIds((prev) => {
      if (prev.includes(id)) {
        return prev.filter((item) => item !== id);
      }
      if (prev.length >= 5) {
        return prev;
      }
      return [...prev, id];
    });
  };

  const selectedRuns = useMemo(
    () => runs.filter((run) => selectedIds.includes(run.id)),
    [runs, selectedIds]
  );

  const overlayData = useMemo(() => {
    return equityCurve.map((point, index) => {
      const row: Record<string, number | string> = { date: point.date };
      selectedRuns.forEach((run, idx) => {
        row[run.id] = point.value + Math.sin(index / 2 + idx) * 2 + idx * 3;
      });
      return row;
    });
  }, [selectedRuns]);

  const configLeft = selectedRuns[0];
  const configRight = selectedRuns[1];

  return (
    <div className="flex min-h-full flex-col gap-5">
      <PageHeader
        eyebrow="Сравнение"
        title="Сравнение запусков"
        description="Выберите несколько прогонов и проверьте, как отличаются результаты, конфиги и датасеты."
      />

      <SurfaceCard title="Выбор запусков" subtitle="Можно выбрать от двух до пяти">
        <div className="mb-2 text-xs text-muted-foreground">Запуски</div>
        <div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-3">
          {runs.map((run) => (
            <label
              key={run.id}
              className="flex cursor-pointer items-center justify-between rounded-[18px] border border-border bg-panel-subtle p-3 text-xs"
            >
              <div>
                <div className="font-mono text-foreground">{run.id}</div>
                <div className="text-muted-foreground">
                  {run.strategy} / {run.datasetVersion}
                </div>
              </div>
              <Checkbox
                checked={selectedIds.includes(run.id)}
                onCheckedChange={() => toggleRun(run.id)}
              />
            </label>
          ))}
        </div>
      </SurfaceCard>

      {selectedRuns.length < 2 ? (
        <EmptyState
          title="Выберите как минимум два запуска"
          description="Наложите графики, сравните метрики и изучите различия конфигов."
        />
      ) : (
        <>
          <ChartCard title="Наложение кривых капитала" subtitle="Выбранные запуски">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={overlayData}>
                <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="4 4" />
                <XAxis
                  dataKey="date"
                  tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
                  axisLine={{ stroke: "hsl(var(--border))" }}
                  tickLine={{ stroke: "hsl(var(--border))" }}
                />
                <YAxis
                  tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
                  axisLine={{ stroke: "hsl(var(--border))" }}
                  tickLine={{ stroke: "hsl(var(--border))" }}
                />
                <Tooltip
                  contentStyle={{
                    background: "hsl(var(--popover))",
                    border: "1px solid hsl(var(--border))",
                    color: "hsl(var(--foreground))",
                    fontSize: "12px",
                  }}
                />
                {selectedRuns.map((run, idx) => (
                  <Line
                    key={run.id}
                    type="monotone"
                    dataKey={run.id}
                    stroke={lineColors[idx % lineColors.length]}
                    strokeWidth={2}
                    dot={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>

          <SurfaceCard title="Метрики" subtitle="Дельта по каждому запуску." contentClassName="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Запуск</TableHead>
                  <TableHead>PnL</TableHead>
                  <TableHead>Шарп</TableHead>
                  <TableHead>Макс. просадка</TableHead>
                  <TableHead>Win Rate</TableHead>
                  <TableHead>Profit Factor</TableHead>
                  <TableHead>Сделки</TableHead>
                  <TableHead>Diagnostics</TableHead>
                  <TableHead>Warnings</TableHead>
                  <TableHead>Stability</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {selectedRuns.map((run) => (
                  <TableRow key={run.id}>
                    <TableCell className="font-mono text-xs text-foreground">
                      {run.id}
                    </TableCell>
                    <TableCell className="text-xs text-profit">
                      {run.metrics.pnl.toFixed(1)}%
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {run.metrics.sharpe.toFixed(2)}
                    </TableCell>
                    <TableCell className="text-xs text-loss">
                      {run.metrics.maxDrawdown.toFixed(1)}%
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatDiagnosticsValue(run.diagnostics?.trades.winRate, { suffix: "%", digits: 1 })}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {formatDiagnosticsValue(run.diagnostics?.trades.profitFactor, { digits: 2 })}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {run.metrics.trades}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {diagnosticsStatusLabel(run.diagnostics?.diagnosticsStatus)}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {run.diagnostics?.warnings.length ?? 0}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {diagnosticsStatusLabel(run.diagnostics?.stability.status)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </SurfaceCard>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <SurfaceCard title="Различия конфигов" subtitle="Параметры бок о бок.">
              <div className="grid grid-cols-2 gap-3 p-4 text-xs">
                <div className="rounded-[18px] border border-border bg-panel-subtle p-3">
                  <div className="mb-2 text-[11px] uppercase text-muted-foreground">
                    {configLeft?.id}
                  </div>
                  <pre className="whitespace-pre-wrap font-mono text-[11px] text-foreground">
{configLeft?.config}
                  </pre>
                </div>
                <div className="rounded-[18px] border border-border bg-panel-subtle p-3">
                  <div className="mb-2 text-[11px] uppercase text-muted-foreground">
                    {configRight?.id}
                  </div>
                  <pre className="whitespace-pre-wrap font-mono text-[11px] text-foreground">
{configRight?.config}
                  </pre>
                </div>
              </div>
            </SurfaceCard>

            <SurfaceCard title="Различия датасетов" subtitle="Версии и качество.">
              <div className="space-y-3 p-4 text-xs text-muted-foreground">
                <div className="rounded-[18px] border border-border bg-panel-subtle p-3">
                  <div className="text-[11px] uppercase">Слева</div>
                  <div className="text-foreground">{configLeft?.datasetVersion}</div>
                  <div>Покрытие 98.7% / Пропуски 0.6%</div>
                </div>
                <div className="rounded-[18px] border border-border bg-panel-subtle p-3">
                  <div className="text-[11px] uppercase">Справа</div>
                  <div className="text-foreground">{configRight?.datasetVersion}</div>
                  <div>Покрытие 97.9% / Пропуски 0.9%</div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">разница хешей пайплайна</Badge>
                  <Badge variant="secondary">всплески выбросов</Badge>
                  <Badge variant="secondary">дельта покрытия</Badge>
                </div>
              </div>
            </SurfaceCard>
          </div>

          <SurfaceCard title="Подсказки по стабильности">
            <ul className="list-disc space-y-1 pl-4 text-xs text-muted-foreground">
              <li>Датасет v13 добавляет всплески волатильности после 2022 года.</li>
              <li>Конфиг отличается по проскальзыванию и модели исполнения.</li>
              <li>Число сделок выросло при более коротком окне истории.</li>
            </ul>
          </SurfaceCard>
        </>
      )}
    </div>
  );
}
