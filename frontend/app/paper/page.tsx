"use client";

import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Pause, Play, Plus, RefreshCw, Square, XCircle } from "lucide-react";
import { useLanguage } from "@/components/language/language-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { PageHeader } from "@/components/shared/page-header";
import { SurfaceCard } from "@/components/shared/surface-card";
import { apiFetch } from "@/lib/api/client";
import { cn } from "@/lib/utils";

type PaperSessionStatus = "CREATED" | "RUNNING" | "PAUSED" | "STOPPED" | "FAILED";
type PaperOrderSide = "BUY" | "SELL";
type PaperOrderType = "MARKET" | "LIMIT";
type PaperOrderStatus =
  | "NEW"
  | "ACCEPTED"
  | "REJECTED"
  | "FILLED"
  | "PARTIALLY_FILLED"
  | "CANCELED";

type PaperSession = {
  id: number;
  name: string;
  exchange: string;
  symbol: string;
  timeframe: string;
  status: PaperSessionStatus;
  initialBalance: number;
  currentBalance: number;
  baseCurrency: string;
  quoteCurrency: string;
  startedAt?: string | null;
  stoppedAt?: string | null;
  createdAt: string;
};

type PaperOrder = {
  id: number;
  sessionId: number;
  symbol: string;
  side: PaperOrderSide;
  type: PaperOrderType;
  status: PaperOrderStatus;
  quantity: number;
  price?: number | null;
  filledQuantity: number;
  averageFillPrice?: number | null;
  rejectedReason?: string | null;
  createdAt: string;
};

type PaperPosition = {
  id: number;
  symbol: string;
  quantity: number;
  averageEntryPrice: number;
  realizedPnl: number;
  unrealizedPnl: number;
};

type PaperFill = {
  id: number;
  orderId: number;
  symbol: string;
  side: PaperOrderSide;
  quantity: number;
  price: number;
  fee: number;
  feeCurrency: string;
  executedAt: string;
};

type PaperSummary = {
  sessionId: number;
  status: PaperSessionStatus;
  initialBalance: number;
  currentBalance: number;
  realizedPnl: number;
  unrealizedPnl: number;
  equity: number;
  openPositions: number;
  ordersCount: number;
  fillsCount: number;
};

const statusTone: Record<string, string> = {
  CREATED: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
  RUNNING: "border-status-success/35 bg-status-success/12 text-status-success",
  PAUSED: "border-status-warning/35 bg-status-warning/12 text-status-warning",
  STOPPED: "border-border bg-muted/20 text-muted-foreground",
  FAILED: "border-status-error/35 bg-status-error/12 text-status-error",
  FILLED: "border-status-success/35 bg-status-success/12 text-status-success",
  ACCEPTED: "border-status-warning/35 bg-status-warning/12 text-status-warning",
  REJECTED: "border-status-error/35 bg-status-error/12 text-status-error",
  CANCELED: "border-border bg-muted/20 text-muted-foreground",
};

function formatMoney(value?: number | null, currency = "USDT") {
  if (value == null || Number.isNaN(Number(value))) {
    return "-";
  }
  return `${Number(value).toLocaleString("en-US", {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  })} ${currency}`;
}

function formatQuantity(value?: number | null) {
  if (value == null || Number.isNaN(Number(value))) {
    return "-";
  }
  return Number(value).toLocaleString("en-US", { maximumFractionDigits: 8 });
}

function StatusBadge({ value }: { value: string }) {
  return (
    <Badge variant="outline" className={cn("rounded-full px-2.5 py-1", statusTone[value])}>
      {value}
    </Badge>
  );
}

async function readJson<T>(response: Response): Promise<T> {
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload?.message ?? "Request failed");
  }
  return payload as T;
}

export default function PaperTradingPage() {
  const { language } = useLanguage();
  const [sessions, setSessions] = useState<PaperSession[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [orders, setOrders] = useState<PaperOrder[]>([]);
  const [positions, setPositions] = useState<PaperPosition[]>([]);
  const [fills, setFills] = useState<PaperFill[]>([]);
  const [summary, setSummary] = useState<PaperSummary | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [sessionForm, setSessionForm] = useState({
    name: "BTC paper",
    exchange: "binance",
    symbol: "BTCUSDT",
    timeframe: "1h",
    initialBalance: "10000",
    baseCurrency: "BTC",
    quoteCurrency: "USDT",
  });
  const [orderForm, setOrderForm] = useState({
    side: "BUY" as PaperOrderSide,
    type: "MARKET" as PaperOrderType,
    quantity: "0.01",
    price: "",
  });

  const selectedSession = useMemo(
    () => sessions.find((session) => session.id === selectedSessionId) ?? sessions[0] ?? null,
    [selectedSessionId, sessions]
  );

  async function refreshSessions() {
    setErrorMessage(null);
    const data = await readJson<PaperSession[]>(await apiFetch("/api/paper/sessions"));
    setSessions(data);
    if (!selectedSessionId && data.length > 0) {
      setSelectedSessionId(data[0].id);
    }
  }

  async function refreshSessionDetails(sessionId: number) {
    const [ordersData, positionsData, fillsData, summaryData] = await Promise.all([
      readJson<PaperOrder[]>(await apiFetch(`/api/paper/sessions/${sessionId}/orders`)),
      readJson<PaperPosition[]>(await apiFetch(`/api/paper/sessions/${sessionId}/positions`)),
      readJson<PaperFill[]>(await apiFetch(`/api/paper/sessions/${sessionId}/fills`)),
      readJson<PaperSummary>(await apiFetch(`/api/paper/sessions/${sessionId}/summary`)),
    ]);
    setOrders(ordersData);
    setPositions(positionsData);
    setFills(fillsData);
    setSummary(summaryData);
  }

  async function refreshAll() {
    setIsLoading(true);
    try {
      await refreshSessions();
      const sessionId = selectedSession?.id ?? selectedSessionId;
      if (sessionId) {
        await refreshSessionDetails(sessionId);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Request failed");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    async function loadSessions() {
      try {
        setErrorMessage(null);
        const data = await readJson<PaperSession[]>(await apiFetch("/api/paper/sessions"));
        setSessions(data);
        if (data.length > 0) {
          setSelectedSessionId(data[0].id);
        }
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : "Request failed");
      }
    }

    loadSessions();
  }, []);

  useEffect(() => {
    if (!selectedSession?.id) {
      setOrders([]);
      setPositions([]);
      setFills([]);
      setSummary(null);
      return;
    }
    refreshSessionDetails(selectedSession.id).catch((error) => {
      setErrorMessage(error instanceof Error ? error.message : "Request failed");
    });
  }, [selectedSession?.id]);

  async function submitSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    const created = await readJson<PaperSession>(
      await apiFetch("/api/paper/sessions", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          ...sessionForm,
          initialBalance: Number(sessionForm.initialBalance),
        }),
      })
    );
    setSelectedSessionId(created.id);
    await refreshSessions();
  }

  async function transitionSession(action: "start" | "pause" | "stop") {
    if (!selectedSession) {
      return;
    }
    setErrorMessage(null);
    await readJson<PaperSession>(
      await apiFetch(`/api/paper/sessions/${selectedSession.id}/${action}`, {
        method: "POST",
      })
    );
    await refreshSessions();
    await refreshSessionDetails(selectedSession.id);
  }

  async function submitOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSession) {
      return;
    }
    setErrorMessage(null);
    const body: Record<string, unknown> = {
      side: orderForm.side,
      type: orderForm.type,
      quantity: Number(orderForm.quantity),
    };
    if (orderForm.type === "LIMIT") {
      body.price = Number(orderForm.price);
    }
    await readJson<PaperOrder>(
      await apiFetch(`/api/paper/sessions/${selectedSession.id}/orders`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      })
    );
    await refreshSessions();
    await refreshSessionDetails(selectedSession.id);
  }

  async function cancelOrder(orderId: number) {
    if (!selectedSession) {
      return;
    }
    await readJson<PaperOrder>(
      await apiFetch(`/api/paper/orders/${orderId}/cancel`, {
        method: "POST",
      })
    );
    await refreshSessionDetails(selectedSession.id);
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-auto p-4 md:p-6">
      <PageHeader
        title={language === "en" ? "Paper Trading" : "Демо-торговля"}
        actions={
          <Button variant="secondary" size="sm" onClick={refreshAll} disabled={isLoading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {language === "en" ? "Refresh" : "Обновить"}
          </Button>
        }
      />

      {errorMessage ? (
        <div className="rounded-[16px] border border-status-error/35 bg-status-error/10 px-4 py-3 text-sm text-status-error">
          {errorMessage}
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <div className="flex flex-col gap-5">
          <SurfaceCard title="New session">
            <form className="grid gap-3" onSubmit={submitSession}>
              <Input
                value={sessionForm.name}
                onChange={(event) => setSessionForm({ ...sessionForm, name: event.target.value })}
                placeholder="Name"
              />
              <div className="grid grid-cols-2 gap-3">
                <Input
                  value={sessionForm.exchange}
                  onChange={(event) =>
                    setSessionForm({ ...sessionForm, exchange: event.target.value })
                  }
                  placeholder="Exchange"
                />
                <Input
                  value={sessionForm.timeframe}
                  onChange={(event) =>
                    setSessionForm({ ...sessionForm, timeframe: event.target.value })
                  }
                  placeholder="Timeframe"
                />
              </div>
              <Input
                value={sessionForm.symbol}
                onChange={(event) => setSessionForm({ ...sessionForm, symbol: event.target.value })}
                placeholder="Symbol"
              />
              <div className="grid grid-cols-3 gap-3">
                <Input
                  value={sessionForm.initialBalance}
                  onChange={(event) =>
                    setSessionForm({ ...sessionForm, initialBalance: event.target.value })
                  }
                  placeholder="Balance"
                  type="number"
                  min="0"
                  step="0.01"
                />
                <Input
                  value={sessionForm.baseCurrency}
                  onChange={(event) =>
                    setSessionForm({ ...sessionForm, baseCurrency: event.target.value })
                  }
                  placeholder="Base"
                />
                <Input
                  value={sessionForm.quoteCurrency}
                  onChange={(event) =>
                    setSessionForm({ ...sessionForm, quoteCurrency: event.target.value })
                  }
                  placeholder="Quote"
                />
              </div>
              <Button type="submit" className="w-full">
                <Plus className="mr-2 h-4 w-4" />
                Create
              </Button>
            </form>
          </SurfaceCard>

          <SurfaceCard title="Sessions" contentClassName="p-0">
            <div className="divide-y divide-border/70">
              {sessions.map((session) => (
                <button
                  key={session.id}
                  type="button"
                  onClick={() => setSelectedSessionId(session.id)}
                  className={cn(
                    "flex w-full items-center justify-between gap-3 px-5 py-4 text-left transition-colors hover:bg-[hsl(var(--tl-bg-2)/0.72)]",
                    selectedSession?.id === session.id ? "bg-[hsl(var(--primary)/0.08)]" : ""
                  )}
                >
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-foreground">
                      {session.name}
                    </div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {session.exchange}:{session.symbol} {session.timeframe}
                    </div>
                  </div>
                  <StatusBadge value={session.status} />
                </button>
              ))}
              {sessions.length === 0 ? (
                <div className="px-5 py-6 text-sm text-muted-foreground">No sessions yet.</div>
              ) : null}
            </div>
          </SurfaceCard>
        </div>

        <div className="flex flex-col gap-5">
          <SurfaceCard
            title={selectedSession?.name ?? "Session"}
            subtitle={
              selectedSession
                ? `${selectedSession.exchange}:${selectedSession.symbol} ${selectedSession.timeframe}`
                : undefined
            }
            actions={
              selectedSession ? (
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => transitionSession("start")}>
                    <Play className="mr-2 h-4 w-4" />
                    Start
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => transitionSession("pause")}>
                    <Pause className="mr-2 h-4 w-4" />
                    Pause
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => transitionSession("stop")}>
                    <Square className="mr-2 h-4 w-4" />
                    Stop
                  </Button>
                </div>
              ) : null
            }
          >
            {selectedSession ? (
              <div className="grid gap-3 md:grid-cols-4">
                <Metric label="Status" value={<StatusBadge value={selectedSession.status} />} />
                <Metric
                  label="Balance"
                  value={formatMoney(selectedSession.currentBalance, selectedSession.quoteCurrency)}
                />
                <Metric
                  label="Equity"
                  value={formatMoney(summary?.equity, selectedSession.quoteCurrency)}
                />
                <Metric label="Open positions" value={summary?.openPositions ?? 0} />
              </div>
            ) : (
              <div className="text-sm text-muted-foreground">Create a paper session to begin.</div>
            )}
          </SurfaceCard>

          {selectedSession ? (
            <SurfaceCard title="Order ticket">
              <form className="grid gap-3 md:grid-cols-[120px_120px_1fr_1fr_auto]" onSubmit={submitOrder}>
                <Select
                  value={orderForm.side}
                  onValueChange={(value) =>
                    setOrderForm({ ...orderForm, side: value as PaperOrderSide })
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="BUY">BUY</SelectItem>
                    <SelectItem value="SELL">SELL</SelectItem>
                  </SelectContent>
                </Select>
                <Select
                  value={orderForm.type}
                  onValueChange={(value) =>
                    setOrderForm({ ...orderForm, type: value as PaperOrderType })
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MARKET">MARKET</SelectItem>
                    <SelectItem value="LIMIT">LIMIT</SelectItem>
                  </SelectContent>
                </Select>
                <Input
                  value={orderForm.quantity}
                  onChange={(event) => setOrderForm({ ...orderForm, quantity: event.target.value })}
                  type="number"
                  min="0"
                  step="0.00000001"
                  placeholder="Quantity"
                />
                <Input
                  value={orderForm.price}
                  onChange={(event) => setOrderForm({ ...orderForm, price: event.target.value })}
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Limit price"
                  disabled={orderForm.type === "MARKET"}
                />
                <Button type="submit">
                  <Plus className="mr-2 h-4 w-4" />
                  Order
                </Button>
              </form>
            </SurfaceCard>
          ) : null}

          <div className="grid gap-5 xl:grid-cols-2">
            <SurfaceCard title="Positions" contentClassName="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Symbol</TableHead>
                    <TableHead>Qty</TableHead>
                    <TableHead>Avg</TableHead>
                    <TableHead>PnL</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {positions.map((position) => (
                    <TableRow key={position.id}>
                      <TableCell>{position.symbol}</TableCell>
                      <TableCell>{formatQuantity(position.quantity)}</TableCell>
                      <TableCell>{formatMoney(position.averageEntryPrice, "")}</TableCell>
                      <TableCell>{formatMoney(position.realizedPnl + position.unrealizedPnl, "")}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </SurfaceCard>

            <SurfaceCard title="Fills" contentClassName="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Side</TableHead>
                    <TableHead>Qty</TableHead>
                    <TableHead>Price</TableHead>
                    <TableHead>Fee</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {fills.map((fill) => (
                    <TableRow key={fill.id}>
                      <TableCell>{fill.side}</TableCell>
                      <TableCell>{formatQuantity(fill.quantity)}</TableCell>
                      <TableCell>{formatMoney(fill.price, "")}</TableCell>
                      <TableCell>{formatMoney(fill.fee, fill.feeCurrency)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </SurfaceCard>
          </div>

          <SurfaceCard title="Orders" contentClassName="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Status</TableHead>
                  <TableHead>Side</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Price</TableHead>
                  <TableHead></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {orders.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell>
                      <StatusBadge value={order.status} />
                      {order.rejectedReason ? (
                        <div className="mt-1 text-xs text-status-error">{order.rejectedReason}</div>
                      ) : null}
                    </TableCell>
                    <TableCell>{order.side}</TableCell>
                    <TableCell>{order.type}</TableCell>
                    <TableCell>{formatQuantity(order.quantity)}</TableCell>
                    <TableCell>
                      {formatMoney(order.averageFillPrice ?? order.price, selectedSession?.quoteCurrency)}
                    </TableCell>
                    <TableCell className="text-right">
                      {order.status === "ACCEPTED" || order.status === "NEW" ? (
                        <Button size="icon" variant="ghost" onClick={() => cancelOrder(order.id)}>
                          <XCircle className="h-4 w-4" />
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </SurfaceCard>
        </div>
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-[16px] border border-border/70 bg-panel-subtle px-4 py-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-2 text-sm font-semibold text-foreground">{value}</div>
    </div>
  );
}
