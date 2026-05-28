"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { useAuth } from "@/features/auth/auth-provider";
import { createRun, fetchRuns, type CreateRunPayload } from "@/lib/api/runs";
import { fetchStrategies } from "@/lib/api/strategies";
import type { Run, Strategy } from "@/lib/types";

type RunStore = {
  runs: Run[];
  isLoading: boolean;
  addRun: (run: Run) => void;
  createRemoteRun: (
    payload: CreateRunPayload,
    options?: { replaceRunId?: string; preserveFields?: Partial<Run> }
  ) => Promise<Run>;
  updateRun: (id: string, update: Partial<Run>) => void;
  deleteRun: (id: string) => void;
  getRunById: (id: string) => Run | undefined;
  reloadRuns: () => Promise<void>;
};

const RunStoreContext = createContext<RunStore | null>(null);
const STORAGE_KEY = "tradelab:runs";

function sortRuns(items: Run[]) {
  return [...items].sort((left, right) => {
    const leftTime = Date.parse(left.createdAt);
    const rightTime = Date.parse(right.createdAt);
    return (Number.isNaN(rightTime) ? 0 : rightTime) - (Number.isNaN(leftTime) ? 0 : leftTime);
  });
}

function readStoredRuns(storageKey: string) {
  const stored = localStorage.getItem(storageKey);
  if (!stored) {
    return [];
  }

  try {
    const parsed = JSON.parse(stored) as Run[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function buildStrategiesMap(strategies: Strategy[]) {
  return new Map(
    strategies
      .filter((strategy) => typeof strategy.id === "number")
      .map((strategy) => [strategy.id as number, strategy] as const)
  );
}

function mergeBackendRunWithLocalState(backendRun: Run, localRun?: Run) {
  if (!localRun) {
    return backendRun;
  }

  return {
    ...backendRun,
    strategy: localRun.strategy || backendRun.strategy,
    tags: localRun.tags.length > 0 ? localRun.tags : backendRun.tags,
    artifacts: localRun.artifacts.length > 0 ? localRun.artifacts : backendRun.artifacts,
    diagnostics: backendRun.diagnostics ?? localRun.diagnostics,
    strategyParams: localRun.strategyParams ?? backendRun.strategyParams,
    commit: localRun.commit || backendRun.commit,
    config: localRun.config || backendRun.config,
    diff: localRun.diff ?? backendRun.diff,
    datasetVersion: localRun.datasetVersion || backendRun.datasetVersion,
  };
}

function mergeRuns(localRuns: Run[], backendRuns: Run[]) {
  const backendIds = new Set(backendRuns.map((run) => run.backendRunId));
  const localByBackendId = new Map(
    localRuns
      .filter((run) => typeof run.backendRunId === "number")
      .map((run) => [run.backendRunId as number, run] as const)
  );

  const mergedBackendRuns = backendRuns.map((run) =>
    mergeBackendRunWithLocalState(run, localByBackendId.get(run.backendRunId as number))
  );

  const draftRuns = localRuns.filter((run) => run.backendRunId === undefined || !backendIds.has(run.backendRunId));
  return sortRuns([...draftRuns, ...mergedBackendRuns]);
}

async function loadRunsFromSources(localRuns: Run[]) {
  const strategies = await fetchStrategies();
  const nextStrategiesById = buildStrategiesMap(strategies);
  const backendRuns = await fetchRuns(nextStrategiesById);

  return {
    strategiesById: nextStrategiesById,
    runs: mergeRuns(localRuns, backendRuns),
  };
}

export function RunStoreProvider({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  const storageKey = session ? `${STORAGE_KEY}:${session.user.id}` : STORAGE_KEY;
  const [runs, setRuns] = useState<Run[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [strategiesById, setStrategiesById] = useState<Map<number, Strategy>>(new Map());

  const reloadRuns = async () => {
    setIsLoading(true);

    const localRuns = readStoredRuns(storageKey);

    try {
      const loaded = await loadRunsFromSources(localRuns);
      setStrategiesById(loaded.strategiesById);
      setRuns(loaded.runs);
    } catch {
      setRuns(sortRuns(localRuns));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    async function loadInitialRuns() {
      setIsLoading(true);

      const localRuns = readStoredRuns(storageKey);

      try {
        const loaded = await loadRunsFromSources(localRuns);
        if (cancelled) {
          return;
        }

        setStrategiesById(loaded.strategiesById);
        setRuns(loaded.runs);
      } catch {
        if (!cancelled) {
          setRuns(sortRuns(localRuns));
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void loadInitialRuns();

    return () => {
      cancelled = true;
    };
  }, [storageKey]);

  useEffect(() => {
    if (isLoading) {
      return;
    }

    localStorage.setItem(storageKey, JSON.stringify(runs));
  }, [isLoading, runs, storageKey]);

  const addRun = (run: Run) => {
    setRuns((current) => sortRuns([run, ...current]));
  };

  const createRemoteRun = async (
    payload: CreateRunPayload,
    options?: { replaceRunId?: string; preserveFields?: Partial<Run> }
  ) => {
    const remoteRun = await createRun(payload, strategiesById);

    setRuns((current) => {
      const existingRun = options?.replaceRunId
        ? current.find((run) => run.id === options.replaceRunId)
        : undefined;

      const mergedRemoteRun = {
        ...remoteRun,
        ...options?.preserveFields,
        tags:
          options?.preserveFields?.tags ??
          existingRun?.tags ??
          remoteRun.tags,
        artifacts:
          options?.preserveFields?.artifacts ??
          existingRun?.artifacts ??
          remoteRun.artifacts,
        config:
          options?.preserveFields?.config ??
          existingRun?.config ??
          remoteRun.config,
        commit:
          options?.preserveFields?.commit ??
          existingRun?.commit ??
          remoteRun.commit,
        diff:
          options?.preserveFields?.diff ??
          existingRun?.diff ??
          remoteRun.diff,
        datasetVersion:
          options?.preserveFields?.datasetVersion ??
          existingRun?.datasetVersion ??
          remoteRun.datasetVersion,
      };

      const nextRuns = options?.replaceRunId
        ? current.filter((run) => run.id !== options.replaceRunId)
        : current.filter((run) => run.id !== remoteRun.id);

      return sortRuns([mergedRemoteRun, ...nextRuns]);
    });

    return remoteRun;
  };

  const updateRun = (id: string, update: Partial<Run>) => {
    setRuns((current) =>
      current.map((run) => (run.id === id ? { ...run, ...update } : run))
    );
  };

  const deleteRun = (id: string) => {
    setRuns((current) => current.filter((run) => run.id !== id));
  };

  const getRunById = (id: string) => runs.find((run) => run.id === id);

  return (
    <RunStoreContext.Provider
      value={{ runs, isLoading, addRun, createRemoteRun, updateRun, deleteRun, getRunById, reloadRuns }}
    >
      {children}
    </RunStoreContext.Provider>
  );
}

export function useRuns() {
  const context = useContext(RunStoreContext);
  if (!context) {
    throw new Error("useRuns must be used within RunStoreProvider");
  }
  return context;
}
