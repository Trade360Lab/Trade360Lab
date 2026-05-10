"use client";

import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ClipboardCheck,
  Download,
  Plus,
  Power,
  RefreshCw,
  ShieldCheck,
  Square,
} from "lucide-react";
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

type LiveSessionStatus = "CREATED" | "ENABLED" | "DISABLED";
type LiveOrderSide = "BUY" | "SELL";
type LiveOrderType = "MARKET" | "LIMIT";
type LiveOrderStatus =
  | "CREATED"
  | "SUBMITTED"
  | "ACCEPTED"
  | "PARTIALLY_FILLED"
  | "FILLED"
  | "CANCELED"
  | "REJECTED"
  | "FAILED";

type LiveCredential = {
  id: number;
  exchange: string;
  keyReference: string;
  active: boolean;
  updatedAt: string;
};

type LiveSession = {
  id: number;
  name: string;
  exchange: string;
  symbol: string;
  baseCurrency: string;
  quoteCurrency: string;
  status: LiveSessionStatus;
  maxOrderNotional: number;
  maxPositionNotional: number;
  maxDailyNotional: number;
};

type LiveOrder = {
  id: number;
  exchange: string;
  symbol: string;
  side: LiveOrderSide;
  type: LiveOrderType;
  quantity: number;
  requestedPrice?: number | null;
  executedPrice?: number | null;
  status: LiveOrderStatus;
  exchangeOrderId?: string | null;
  rejectedReason?: string | null;
  updatedAt: string;
};

type LivePosition = {
  id: number;
  exchange: string;
  symbol: string;
  quantity: number;
  averageEntryPrice: number;
  realizedPnl: number;
  unrealizedPnl: number;
  syncStatus: string;
};

type LiveRiskStatus = {
  killSwitchActive: boolean;
  killSwitchReason?: string | null;
  circuitBreakers: { exchange: string; active: boolean; reason?: string | null }[];
};

type LiveRiskEvent = {
  id: number;
  orderId?: number | null;
  exchange: string;
  symbol?: string | null;
  eventType: string;
  reason?: string | null;
  createdAt: string;
};

type ExchangeHealth = {
  exchange: string;
  connected: boolean;
  credentialsValid: boolean;
  realOrderSubmissionEnabled: boolean;
  message: string;
};

type BinanceCertification = {
  exchange: string;
  testnetOnly: boolean;
  realOrderSubmissionEnabled: boolean;
  credentialsPresent: boolean;
  credentialsValid: boolean;
  accountSnapshotReachable: boolean;
  openOrdersSnapshotReachable: boolean;
  certified: boolean;
  accountSnapshotSummary?: string | null;
  openOrdersSnapshotSummary?: string | null;
  message: string;
  checkedAt: string;
};

type CertificationReport = {
  id: number;
  exchange: string;
  environment: string;
  startedAt: string;
  finishedAt: string;
  connectivityStatus: string;
  accountSnapshotStatus: string;
  openOrdersStatus: string;
  reconciliationStatus: string;
  riskChecksStatus: string;
  finalResult: string;
  realOrderSubmissionEnabled: boolean;
  message?: string | null;
};

const statusTone: Record<string, string> = {
  CREATED: "border-muted-foreground/30 bg-muted/20 text-muted-foreground",
  ENABLED: "border-status-success/35 bg-status-success/12 text-status-success",
  DISABLED: "border-border bg-muted/20 text-muted-foreground",
  SUBMITTED: "border-status-warning/35 bg-status-warning/12 text-status-warning",
  ACCEPTED: "border-status-warning/35 bg-status-warning/12 text-status-warning",
  FILLED: "border-status-success/35 bg-status-success/12 text-status-success",
  PARTIALLY_FILLED: "border-status-warning/35 bg-status-warning/12 text-status-warning",
  CANCELED: "border-border bg-muted/20 text-muted-foreground",
  REJECTED: "border-status-error/35 bg-status-error/12 text-status-error",
  FAILED: "border-status-error/35 bg-status-error/12 text-status-error",
};

function StatusBadge({ value }: { value: string }) {
  return (
    <Badge variant="outline" className={cn("rounded-full px-2.5 py-1", statusTone[value])}>
      {value}
    </Badge>
  );
}

function formatMoney(value?: number | null, currency = "USDT") {
  if (value == null || Number.isNaN(Number(value))) {
    return "-";
  }
  return `${Number(value).toLocaleString("en-US", {
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  })} ${currency}`;
}

async function readJson<T>(response: Response): Promise<T> {
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload?.message ?? "Request failed");
  }
  return payload as T;
}

export default function LiveTradingPage() {
  const [credentials, setCredentials] = useState<LiveCredential[]>([]);
  const [sessions, setSessions] = useState<LiveSession[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [orders, setOrders] = useState<LiveOrder[]>([]);
  const [positions, setPositions] = useState<LivePosition[]>([]);
  const [risk, setRisk] = useState<LiveRiskStatus | null>(null);
  const [riskEvents, setRiskEvents] = useState<LiveRiskEvent[]>([]);
  const [health, setHealth] = useState<ExchangeHealth | null>(null);
  const [certification, setCertification] = useState<BinanceCertification | null>(null);
  const [certificationReport, setCertificationReport] = useState<CertificationReport | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [credentialForm, setCredentialForm] = useState({
    exchange: "binance",
    apiKey: "",
    apiSecret: "",
    active: "true",
  });
  const [sessionForm, setSessionForm] = useState({
    name: "BTC live guarded",
    exchange: "binance",
    symbol: "BTCUSDT",
    baseCurrency: "BTC",
    quoteCurrency: "USDT",
    maxOrderNotional: "100",
    maxPositionNotional: "500",
    maxDailyNotional: "1000",
    symbolWhitelist: "BTCUSDT",
  });
  const [orderForm, setOrderForm] = useState({
    side: "BUY" as LiveOrderSide,
    type: "MARKET" as LiveOrderType,
    quantity: "0.001",
    requestedPrice: "",
  });

  const selectedSession = useMemo(
    () => sessions.find((session) => session.id === selectedSessionId) ?? sessions[0] ?? null,
    [selectedSessionId, sessions]
  );

  const refreshAll = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [credentialData, sessionData, orderData, positionData, riskData, riskEventData, healthData, reportData] =
        await Promise.all([
          readJson<LiveCredential[]>(await apiFetch("/api/live/credentials/status")),
          readJson<LiveSession[]>(await apiFetch("/api/live/sessions")),
          readJson<LiveOrder[]>(await apiFetch("/api/live/orders")),
          readJson<LivePosition[]>(await apiFetch("/api/live/positions")),
          readJson<LiveRiskStatus>(await apiFetch("/api/live/risk/status")),
          readJson<LiveRiskEvent[]>(await apiFetch("/api/live/risk/events")),
          readJson<ExchangeHealth>(await apiFetch("/api/live/exchange/health?exchange=binance")),
          readJson<CertificationReport>(await apiFetch("/api/live/certification/testnet/latest")).catch(() => null),
        ]);
      setCredentials(credentialData);
      setSessions(sessionData);
      setOrders(orderData);
      setPositions(positionData);
      setRisk(riskData);
      setRiskEvents(riskEventData);
      setHealth(healthData);
      setCertificationReport(reportData);
      if (!selectedSessionId && sessionData.length > 0) {
        setSelectedSessionId(sessionData[0].id);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Request failed");
    } finally {
      setIsLoading(false);
    }
  }, [selectedSessionId]);

  useEffect(() => {
    refreshAll();
  }, [refreshAll]);

  async function submitCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await readJson<LiveCredential>(
      await apiFetch("/api/live/credentials", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ ...credentialForm, active: credentialForm.active === "true" }),
      })
    );
    setCredentialForm({ ...credentialForm, apiKey: "", apiSecret: "" });
    await refreshAll();
  }

  async function submitSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const created = await readJson<LiveSession>(
      await apiFetch("/api/live/sessions", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          ...sessionForm,
          maxOrderNotional: Number(sessionForm.maxOrderNotional),
          maxPositionNotional: Number(sessionForm.maxPositionNotional),
          maxDailyNotional: Number(sessionForm.maxDailyNotional),
        }),
      })
    );
    setSelectedSessionId(created.id);
    await refreshAll();
  }

  async function transitionSession(action: "enable" | "disable") {
    if (!selectedSession) {
      return;
    }
    await readJson<LiveSession>(
      await apiFetch(`/api/live/sessions/${selectedSession.id}/${action}`, { method: "POST" })
    );
    await refreshAll();
  }

  async function submitOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSession) {
      return;
    }
    const body: Record<string, unknown> = {
      sessionId: selectedSession.id,
      side: orderForm.side,
      type: orderForm.type,
      quantity: Number(orderForm.quantity),
    };
    if (orderForm.type === "LIMIT") {
      body.requestedPrice = Number(orderForm.requestedPrice);
    }
    await readJson<LiveOrder>(
      await apiFetch("/api/live/orders", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      })
    );
    await refreshAll();
  }

  async function activateKillSwitch() {
    await readJson<LiveRiskStatus>(
      await apiFetch("/api/live/kill-switch/activate", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ reason: "Manual emergency stop", cancelOpenOrders: false }),
      })
    );
    await refreshAll();
  }

  async function resetKillSwitch() {
    await readJson<LiveRiskStatus>(await apiFetch("/api/live/kill-switch/reset", { method: "POST" }));
    await refreshAll();
  }

  async function certifyBinanceTestnet() {
    const report = await readJson<CertificationReport>(
      await apiFetch("/api/live/certification/testnet/run", { method: "POST" })
    );
    setCertificationReport(report);
    setCertification(null);
  }

  function exportAuditEvents() {
    window.open("/api/live/audit-events/export", "_blank", "noopener,noreferrer");
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-auto p-4 md:p-6">
      <PageHeader
        eyebrow="Live trading"
        title="Live Trading"
        description="Controlled live execution with credentials, risk checks, circuit breakers, and emergency stop."
        actions={
          <Button variant="secondary" size="sm" onClick={refreshAll} disabled={isLoading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Refresh
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
          <SurfaceCard title="Exchange credentials">
            <form className="grid gap-3" onSubmit={submitCredentials}>
              <Input
                value={credentialForm.exchange}
                onChange={(event) =>
                  setCredentialForm({ ...credentialForm, exchange: event.target.value })
                }
                placeholder="Exchange"
              />
              <Input
                value={credentialForm.apiKey}
                onChange={(event) =>
                  setCredentialForm({ ...credentialForm, apiKey: event.target.value })
                }
                placeholder="API key"
                type="password"
              />
              <Input
                value={credentialForm.apiSecret}
                onChange={(event) =>
                  setCredentialForm({ ...credentialForm, apiSecret: event.target.value })
                }
                placeholder="API secret"
                type="password"
              />
              <Button type="submit" className="w-full">
                <ShieldCheck className="mr-2 h-4 w-4" />
                Store encrypted
              </Button>
            </form>
          </SurfaceCard>

          <SurfaceCard title="New live session">
            <form className="grid gap-3" onSubmit={submitSession}>
              <Input value={sessionForm.name} onChange={(event) => setSessionForm({ ...sessionForm, name: event.target.value })} />
              <div className="grid grid-cols-2 gap-3">
                <Input value={sessionForm.exchange} onChange={(event) => setSessionForm({ ...sessionForm, exchange: event.target.value })} />
                <Input value={sessionForm.symbol} onChange={(event) => setSessionForm({ ...sessionForm, symbol: event.target.value })} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <Input value={sessionForm.baseCurrency} onChange={(event) => setSessionForm({ ...sessionForm, baseCurrency: event.target.value })} />
                <Input value={sessionForm.quoteCurrency} onChange={(event) => setSessionForm({ ...sessionForm, quoteCurrency: event.target.value })} />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <Input type="number" min="0" step="0.01" value={sessionForm.maxOrderNotional} onChange={(event) => setSessionForm({ ...sessionForm, maxOrderNotional: event.target.value })} />
                <Input type="number" min="0" step="0.01" value={sessionForm.maxPositionNotional} onChange={(event) => setSessionForm({ ...sessionForm, maxPositionNotional: event.target.value })} />
                <Input type="number" min="0" step="0.01" value={sessionForm.maxDailyNotional} onChange={(event) => setSessionForm({ ...sessionForm, maxDailyNotional: event.target.value })} />
              </div>
              <Input value={sessionForm.symbolWhitelist} onChange={(event) => setSessionForm({ ...sessionForm, symbolWhitelist: event.target.value })} />
              <Button type="submit" className="w-full">
                <Plus className="mr-2 h-4 w-4" />
                Create
              </Button>
            </form>
          </SurfaceCard>
        </div>

        <div className="flex flex-col gap-5">
          <SurfaceCard
            title="Safety state"
            actions={
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="destructive" onClick={activateKillSwitch}>
                  <AlertTriangle className="mr-2 h-4 w-4" />
                  Kill switch
                </Button>
                <Button size="sm" variant="secondary" onClick={resetKillSwitch}>
                  <Power className="mr-2 h-4 w-4" />
                  Reset
                </Button>
              </div>
            }
          >
            <div className="grid gap-3 md:grid-cols-4">
              <Metric label="Kill switch" value={risk?.killSwitchActive ? "ACTIVE" : "Clear"} />
              <Metric label="Exchange" value={health?.connected ? "Connected" : "Unavailable"} />
              <Metric label="Credentials" value={health?.credentialsValid ? "Valid" : "Missing"} />
              <Metric label="Submission" value={health?.realOrderSubmissionEnabled ? "Enabled" : "Disabled"} />
            </div>
            <div className="mt-4 rounded-[16px] border border-status-warning/30 bg-status-warning/10 px-4 py-3 text-xs text-status-warning">
              Real order submission remains disabled by default. Testnet certification does not imply production readiness.
            </div>
          </SurfaceCard>

          <SurfaceCard
            title="Binance testnet certification"
            subtitle="Read-only account and open-order snapshots"
            actions={
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" onClick={exportAuditEvents}>
                  <Download className="mr-2 h-4 w-4" />
                  Export audit
                </Button>
                <Button size="sm" variant="secondary" onClick={certifyBinanceTestnet}>
                  <ClipboardCheck className="mr-2 h-4 w-4" />
                  Certify
                </Button>
              </div>
            }
          >
            <div className="grid gap-3 md:grid-cols-4">
              <Metric label="Scope" value={certificationReport ? `${certificationReport.exchange} ${certificationReport.environment}` : certification?.testnetOnly ? "Testnet only" : "Not checked"} />
              <Metric label="Account" value={certificationReport?.accountSnapshotStatus ?? (certification?.accountSnapshotReachable ? "PASS" : "Not verified")} />
              <Metric label="Open orders" value={certificationReport?.openOrdersStatus ?? (certification?.openOrdersSnapshotReachable ? "PASS" : "Not verified")} />
              <Metric label="Result" value={certificationReport?.finalResult ?? (certification?.certified ? "PASS" : "Pending")} />
            </div>
            {certificationReport ? (
              <div className="mt-4 rounded-[16px] border border-border/70 bg-panel-subtle px-4 py-3 text-xs text-muted-foreground">
                Report #{certificationReport.id} finished {new Date(certificationReport.finishedAt).toLocaleString()}: {certificationReport.message ?? certificationReport.finalResult}
              </div>
            ) : certification ? (
              <div className="mt-4 rounded-[16px] border border-border/70 bg-panel-subtle px-4 py-3 text-xs text-muted-foreground">
                {certification.message}
                {certification.accountSnapshotSummary ? `; ${certification.accountSnapshotSummary}` : ""}
                {certification.openOrdersSnapshotSummary ? `; ${certification.openOrdersSnapshotSummary}` : ""}
              </div>
            ) : null}
          </SurfaceCard>

          <SurfaceCard
            title={selectedSession?.name ?? "Live session"}
            subtitle={selectedSession ? `${selectedSession.exchange}:${selectedSession.symbol}` : undefined}
            actions={
              selectedSession ? (
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => transitionSession("enable")}>
                    <Power className="mr-2 h-4 w-4" />
                    Enable
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => transitionSession("disable")}>
                    <Square className="mr-2 h-4 w-4" />
                    Disable
                  </Button>
                </div>
              ) : null
            }
          >
            <div className="grid gap-3 md:grid-cols-4">
              <Metric label="Status" value={selectedSession ? <StatusBadge value={selectedSession.status} /> : "-"} />
              <Metric label="Credentials" value={credentials[0]?.keyReference ?? "-"} />
              <Metric label="Order cap" value={formatMoney(selectedSession?.maxOrderNotional)} />
              <Metric label="Position cap" value={formatMoney(selectedSession?.maxPositionNotional)} />
            </div>
          </SurfaceCard>

          {selectedSession ? (
            <SurfaceCard title="Live order ticket">
              <form className="grid gap-3 md:grid-cols-[120px_120px_1fr_1fr_auto]" onSubmit={submitOrder}>
                <Select value={orderForm.side} onValueChange={(value) => setOrderForm({ ...orderForm, side: value as LiveOrderSide })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="BUY">BUY</SelectItem><SelectItem value="SELL">SELL</SelectItem></SelectContent>
                </Select>
                <Select value={orderForm.type} onValueChange={(value) => setOrderForm({ ...orderForm, type: value as LiveOrderType })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="MARKET">MARKET</SelectItem><SelectItem value="LIMIT">LIMIT</SelectItem></SelectContent>
                </Select>
                <Input type="number" min="0" step="0.00000001" value={orderForm.quantity} onChange={(event) => setOrderForm({ ...orderForm, quantity: event.target.value })} />
                <Input type="number" min="0" step="0.01" value={orderForm.requestedPrice} disabled={orderForm.type === "MARKET"} onChange={(event) => setOrderForm({ ...orderForm, requestedPrice: event.target.value })} />
                <Button type="submit"><Plus className="mr-2 h-4 w-4" />Order</Button>
              </form>
            </SurfaceCard>
          ) : null}

          <div className="grid gap-5 xl:grid-cols-2">
            <LiveTable title="Sessions" empty="No live sessions yet.">
              {sessions.map((session) => (
                <TableRow key={session.id} className="cursor-pointer" onClick={() => setSelectedSessionId(session.id)}>
                  <TableCell>{session.name}</TableCell>
                  <TableCell>{session.symbol}</TableCell>
                  <TableCell><StatusBadge value={session.status} /></TableCell>
                </TableRow>
              ))}
            </LiveTable>
            <LiveTable title="Positions" empty="No synced live positions.">
              {positions.map((position) => (
                <TableRow key={position.id}>
                  <TableCell>{position.exchange}</TableCell>
                  <TableCell>{position.symbol}</TableCell>
                  <TableCell>{Number(position.quantity).toLocaleString("en-US", { maximumFractionDigits: 8 })}</TableCell>
                </TableRow>
              ))}
            </LiveTable>
          </div>

          <LiveTable title="Orders" empty="No live orders yet.">
            {orders.map((order) => (
              <TableRow key={order.id}>
                <TableCell><StatusBadge value={order.status} /></TableCell>
                <TableCell>{order.symbol}</TableCell>
                <TableCell>{order.side} {order.type}</TableCell>
                <TableCell>{Number(order.quantity).toLocaleString("en-US", { maximumFractionDigits: 8 })}</TableCell>
                <TableCell>
                  {order.rejectedReason ? (
                    <span className="text-xs text-status-error">{order.rejectedReason}</span>
                  ) : order.exchangeOrderId ?? "-"}
                </TableCell>
              </TableRow>
            ))}
          </LiveTable>

          <LiveTable title="Risk audit" empty="No live risk events yet.">
            {riskEvents.map((event) => (
              <TableRow key={event.id}>
                <TableCell><StatusBadge value={event.eventType.includes("REJECTED") ? "REJECTED" : "ACCEPTED"} /></TableCell>
                <TableCell>{event.symbol ?? event.exchange}</TableCell>
                <TableCell>{event.eventType}</TableCell>
                <TableCell>{event.orderId ?? "-"}</TableCell>
                <TableCell>
                  <span className="text-xs text-muted-foreground">{event.reason ?? "-"}</span>
                </TableCell>
              </TableRow>
            ))}
          </LiveTable>
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

function LiveTable({ title, empty, children }: { title: string; empty: string; children: ReactNode }) {
  return (
    <SurfaceCard title={title} contentClassName="p-0">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Status</TableHead>
            <TableHead>Symbol</TableHead>
            <TableHead>Detail</TableHead>
            <TableHead>Qty</TableHead>
            <TableHead></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>{children}</TableBody>
      </Table>
      {Array.isArray(children) && children.length === 0 ? (
        <div className="px-5 py-6 text-sm text-muted-foreground">{empty}</div>
      ) : null}
    </SurfaceCard>
  );
}
