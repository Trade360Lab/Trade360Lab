"use client";

import { ReactNode, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, RefreshCw, ShieldAlert, Wifi } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/empty-state";
import { LoadingState } from "@/components/shared/loading-state";
import { PageHeader } from "@/components/shared/page-header";
import { SurfaceCard } from "@/components/shared/surface-card";
import { apiFetch } from "@/lib/api/client";
import { releaseInfo } from "@/lib/release";
import { cn } from "@/lib/utils";

type JavaHealth = {
  status: string;
  service: string;
};

type JavaReadiness = {
  status: string;
  service: string;
  apiVersion: string;
  database: string;
  migrationStatus: string;
  liveTradingMode: string;
  realOrderSubmissionEnabled: boolean;
  safety: string;
};

type PythonReadiness = {
  status: string;
  service: string;
  parserVersion: string;
  database: string;
  engineVersion: string;
  internalAuthConfigured: string;
};

type LiveRiskStatus = {
  killSwitchActive: boolean;
  killSwitchReason?: string | null;
  circuitBreakers: { exchange: string; active: boolean; reason?: string | null }[];
};

type ExchangeHealth = {
  exchange: string;
  connected: boolean;
  credentialsValid: boolean;
  realOrderSubmissionEnabled: boolean;
  message: string;
};

type ServiceState = "ok" | "warn" | "error";
type ServiceKey = "java" | "python" | "risk" | "exchange" | "readiness";

type ServiceResult<T> = {
  data: T | null;
  error: string | null;
};

async function readJson<T>(response: Response): Promise<T> {
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload?.message ?? "Request failed");
  }
  return payload as T;
}

async function readService<T>(path: string): Promise<ServiceResult<T>> {
  try {
    return {
      data: await readJson<T>(await apiFetch(path)),
      error: null,
    };
  } catch (error) {
    return {
      data: null,
      error: error instanceof Error ? error.message : "Service check failed",
    };
  }
}

export default function SettingsPage() {
  const [javaHealth, setJavaHealth] = useState<JavaHealth | null>(null);
  const [javaReadiness, setJavaReadiness] = useState<JavaReadiness | null>(null);
  const [pythonReadiness, setPythonReadiness] = useState<PythonReadiness | null>(null);
  const [liveRisk, setLiveRisk] = useState<LiveRiskStatus | null>(null);
  const [exchangeHealth, setExchangeHealth] = useState<ExchangeHealth | null>(null);
  const [healthErrors, setHealthErrors] = useState<Partial<Record<ServiceKey, string>>>({});
  const [isLoading, setIsLoading] = useState(false);

  async function refresh() {
    setIsLoading(true);
    const [javaResult, javaReadinessResult, pythonReadinessResult, riskResult, exchangeResult] = await Promise.all([
      readService<JavaHealth>("/api/health"),
      readService<JavaReadiness>("/api/readiness"),
      readService<PythonReadiness>("/api/python/readiness"),
      readService<LiveRiskStatus>("/api/live/risk/status"),
      readService<ExchangeHealth>("/api/live/exchange/health?exchange=binance"),
    ]);

    setJavaHealth(javaResult.data);
    setJavaReadiness(javaReadinessResult.data);
    setPythonReadiness(pythonReadinessResult.data);
    setLiveRisk(riskResult.data);
    setExchangeHealth(exchangeResult.data);
    setHealthErrors({
      java: javaResult.error ?? undefined,
      readiness: javaReadinessResult.error ?? undefined,
      python: pythonReadinessResult.error ?? undefined,
      risk: riskResult.error ?? undefined,
      exchange: exchangeResult.error ?? undefined,
    });
    setIsLoading(false);
  }

  useEffect(() => {
    const initialRefresh = window.setTimeout(() => {
      void refresh();
    }, 0);

    return () => window.clearTimeout(initialRefresh);
  }, []);

  const circuitBreakerActive = useMemo(
    () => liveRisk?.circuitBreakers.some((breaker) => breaker.active) ?? false,
    [liveRisk]
  );
  const outageMessages = Object.entries(healthErrors).filter((entry): entry is [ServiceKey, string] => Boolean(entry[1]));

  return (
    <div className="flex min-h-full flex-col gap-5">
      <PageHeader
        eyebrow="Release operations"
        title="Readiness Dashboard"
        description="Release status, service reachability, and live trading safety state."
        actions={
          <Button variant="secondary" size="sm" onClick={refresh} disabled={isLoading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Refresh
          </Button>
        }
      />

      {outageMessages.length > 0 ? (
        <div className="rounded-[16px] border border-status-warning/35 bg-status-warning/10 px-4 py-3 text-sm text-status-warning">
          Partial outage detected: {outageMessages.map(([service]) => service).join(", ")}
        </div>
      ) : null}

      {isLoading && !javaHealth ? <LoadingState label="Checking services..." /> : null}

      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        <SurfaceCard title="Release" subtitle={releaseInfo.name}>
          <div className="grid gap-3">
            <Metric label="Version" value={`v${releaseInfo.version}`} />
            <Metric
              label="Live mode"
              value={javaReadiness?.liveTradingMode ?? (exchangeHealth?.realOrderSubmissionEnabled ? "Real submission enabled" : "Guarded testnet mode")}
              state={javaReadiness?.realOrderSubmissionEnabled || exchangeHealth?.realOrderSubmissionEnabled ? "warn" : "ok"}
            />
            <Metric label="Database" value={javaReadiness?.database ?? "Not checked"} state={javaReadiness?.database === "ok" ? "ok" : "warn"} />
            <Metric label="Migrations" value={javaReadiness?.migrationStatus ?? "Not checked"} />
          </div>
        </SurfaceCard>

        <SurfaceCard title="Services">
          <div className="grid gap-3 md:grid-cols-3">
            <ServiceTile
              title="Frontend"
              detail={`v${releaseInfo.version}`}
              state="ok"
              icon={<Wifi className="h-4 w-4" />}
            />
            <ServiceTile
              title={javaHealth?.service ?? "java-api"}
              detail={healthErrors.java ?? javaHealth?.status ?? (isLoading ? "checking" : "unavailable")}
              state={healthErrors.java ? "error" : javaHealth?.status === "ok" ? "ok" : isLoading ? "warn" : "error"}
              icon={<CheckCircle2 className="h-4 w-4" />}
            />
            <ServiceTile
              title={pythonReadiness?.service ?? "python-parser"}
              detail={healthErrors.python ?? pythonReadiness?.status ?? (isLoading ? "checking" : "unavailable")}
              state={healthErrors.python ? "error" : pythonReadiness?.status === "ready" ? "ok" : isLoading ? "warn" : "error"}
              icon={<CheckCircle2 className="h-4 w-4" />}
            />
          </div>
        </SurfaceCard>
      </div>

      <SurfaceCard title="Live Trading Safety">
        <div className="grid gap-3 md:grid-cols-4">
          <Metric
            label="Kill switch"
            value={healthErrors.risk ? "Unavailable" : liveRisk?.killSwitchActive ? "ACTIVE" : "Clear"}
            state={healthErrors.risk || liveRisk?.killSwitchActive ? "error" : "ok"}
          />
          <Metric
            label="Circuit breaker"
            value={healthErrors.risk ? "Unavailable" : circuitBreakerActive ? "ACTIVE" : "Clear"}
            state={healthErrors.risk || circuitBreakerActive ? "error" : "ok"}
          />
          <Metric
            label="Credentials"
            value={healthErrors.exchange ? "Unavailable" : exchangeHealth?.credentialsValid ? "Valid" : "Missing or invalid"}
            state={healthErrors.exchange ? "error" : exchangeHealth?.credentialsValid ? "ok" : "warn"}
          />
          <Metric
            label="Submission"
            value={
              healthErrors.exchange
                ? "Unavailable"
                : exchangeHealth?.realOrderSubmissionEnabled
                  ? "Enabled"
                  : "Disabled by default"
            }
            state={healthErrors.exchange ? "error" : exchangeHealth?.realOrderSubmissionEnabled ? "warn" : "ok"}
          />
          <Metric
            label="Parser DB"
            value={healthErrors.python ? "Unavailable" : pythonReadiness?.database ?? "Not checked"}
            state={healthErrors.python ? "error" : pythonReadiness?.database === "ok" ? "ok" : "warn"}
          />
          <Metric
            label="Internal auth"
            value={healthErrors.python ? "Unavailable" : pythonReadiness?.internalAuthConfigured ?? "Not checked"}
            state={healthErrors.python ? "error" : pythonReadiness?.internalAuthConfigured === "configured" ? "ok" : "warn"}
          />
          <Metric
            label="Engine"
            value={healthErrors.python ? "Unavailable" : pythonReadiness?.engineVersion ?? "Not checked"}
          />
        </div>
        {liveRisk?.killSwitchReason ? (
          <div className="mt-4 rounded-[16px] border border-status-warning/35 bg-status-warning/10 px-4 py-3 text-sm text-status-warning">
            {liveRisk.killSwitchReason}
          </div>
        ) : null}
        {liveRisk && liveRisk.circuitBreakers.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="No circuit breaker state" description="No exchange circuit breakers have been recorded." />
          </div>
        ) : null}
      </SurfaceCard>
    </div>
  );
}

function ServiceTile({
  title,
  detail,
  state,
  icon,
}: {
  title: string;
  detail: string;
  state: ServiceState;
  icon: ReactNode;
}) {
  return (
    <div className="rounded-[16px] border border-border/70 bg-panel-subtle p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
          {icon}
          {title}
        </div>
        <StateBadge state={state} />
      </div>
      <div className="mt-3 text-xs text-muted-foreground">{detail}</div>
    </div>
  );
}

function Metric({
  label,
  value,
  state = "ok",
}: {
  label: string;
  value: ReactNode;
  state?: ServiceState;
}) {
  return (
    <div className="rounded-[16px] border border-border/70 bg-panel-subtle px-4 py-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div
        className={cn(
          "mt-2 text-sm font-semibold",
          state === "error" ? "text-status-error" : state === "warn" ? "text-status-warning" : "text-foreground"
        )}
      >
        {value}
      </div>
    </div>
  );
}

function StateBadge({ state }: { state: ServiceState }) {
  const label = state === "ok" ? "OK" : state === "warn" ? "WARN" : "ERROR";
  return (
    <Badge
      variant="outline"
      className={cn(
        "rounded-full px-2.5 py-1",
        state === "ok" && "border-status-success/35 bg-status-success/12 text-status-success",
        state === "warn" && "border-status-warning/35 bg-status-warning/12 text-status-warning",
        state === "error" && "border-status-error/35 bg-status-error/12 text-status-error"
      )}
    >
      {state === "error" ? <ShieldAlert className="mr-1 h-3 w-3" /> : null}
      {state === "warn" ? <AlertTriangle className="mr-1 h-3 w-3" /> : null}
      {label}
    </Badge>
  );
}
