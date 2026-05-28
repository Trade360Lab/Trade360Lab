export type RunStatus = "queued" | "running" | "done" | "failed" | "canceled";

export type RunMetrics = {
  pnl: number;
  sharpe: number;
  maxDrawdown: number;
  trades: number;
  winrate: number;
  avgTrade: number;
  feesImpact: number;
};

export type RunParams = {
  fees: string;
  slippage: string;
  execution: string;
  riskPerTrade: string;
  maxExposure: string;
  symbols: string[];
  timeframe: string;
  period: string;
};

export type RunArtifact = {
  id: string;
  label: string;
  type: "log" | "report" | "model" | "export";
  size: string;
  downloadUrl?: string;
};

export type RunDiagnosticsWarning = {
  code: string;
  message: string;
  severity: string;
};

export type RunDiagnosticsRisk = {
  maxDrawdown: number | null;
  maxDrawdownPct: number | null;
  drawdownStart: string | null;
  drawdownEnd: string | null;
  recoveryBars: number | null;
};

export type RunDiagnosticsTrades = {
  tradeCount: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number | null;
  profitFactor: number | null;
  averageWin: number | null;
  averageLoss: number | null;
  bestTrade: number | null;
  worstTrade: number | null;
  longestWinStreak: number;
  longestLossStreak: number;
  averageTradePnl: number | null;
  medianTradePnl: number | null;
  flatTrades: number;
};

export type RunDiagnosticsStabilitySegment = {
  segmentIndex: number;
  from: string | null;
  to: string | null;
  pnl: number | null;
  tradeCount: number;
  maxDrawdown: number | null;
  status: string;
};

export type RunDiagnosticsStability = {
  segments: RunDiagnosticsStabilitySegment[];
  status: string;
};

export type RunDiagnostics = {
  diagnosticsStatus: string;
  diagnosticsSummary: string;
  totalPnL: number | null;
  totalReturnPct: number | null;
  risk: RunDiagnosticsRisk;
  trades: RunDiagnosticsTrades;
  stability: RunDiagnosticsStability;
  warnings: RunDiagnosticsWarning[];
};

export type Run = {
  id: string;
  backendRunId?: number;
  strategyId?: number;
  strategyVersionId?: number | null;
  parameterPresetId?: number | null;
  strategy: string;
  datasetVersion: string;
  period: string;
  timeframe: string;
  params: RunParams;
  strategyParams?: Record<string, unknown>;
  metrics: RunMetrics;
  diagnostics?: RunDiagnostics | null;
  status: RunStatus;
  artifacts: RunArtifact[];
  createdAt: string;
  finishedAt?: string | null;
  errorMessage?: string | null;
  exchange?: string;
  symbol?: string;
  from?: string;
  to?: string;
  commit: string;
  config: string;
  tags: string[];
  diff: {
    code: boolean;
    data: boolean;
    config: boolean;
  };
};
