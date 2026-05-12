"use client";

import { ChangeEvent, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { FileCode2, Upload } from "lucide-react";
import { useLanguage } from "@/components/language/language-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/shared/empty-state";
import { LoadingState } from "@/components/shared/loading-state";
import { PageHeader } from "@/components/shared/page-header";
import { SurfaceCard } from "@/components/shared/surface-card";
import { fetchStrategies, fetchStrategyTemplates, uploadStrategy } from "@/lib/api/strategies";
import type { Strategy, StrategyTemplate } from "@/lib/types";

function formatDate(value: string | null | undefined) {
  if (!value) {
    return "n/a";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

function statusVariant(status: string): BadgeVariant {
  if (status === "VALID" || status === "ACTIVE") {
    return "default";
  }
  if (status === "INVALID" || status === "ARCHIVED") {
    return "destructive";
  }
  return "secondary";
}

export default function StrategiesPage() {
  const { language } = useLanguage();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [templates, setTemplates] = useState<StrategyTemplate[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setIsLoading(true);
    setError(null);
    try {
      const [strategyList, templateList] = await Promise.all([
        fetchStrategies(),
        fetchStrategyTemplates(),
      ]);
      setStrategies(strategyList);
      setTemplates(templateList);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Failed to load strategies");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function handleFileSelect(event: ChangeEvent<HTMLInputElement>) {
    const selectedFile = event.target.files?.[0];
    event.target.value = "";
    if (!selectedFile) {
      return;
    }

    setIsUploading(true);
    setError(null);
    try {
      const strategy = await uploadStrategy(selectedFile);
      setStrategies((current) => [strategy, ...current.filter((item) => item.id !== strategy.id)]);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Failed to upload strategy");
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <div className="flex min-h-full flex-col gap-5">
      <PageHeader
        title={language === "en" ? "Strategies" : "Стратегии"}
        actions={
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept=".py"
              className="hidden"
              onChange={handleFileSelect}
            />
            <Button onClick={() => fileInputRef.current?.click()} disabled={isUploading}>
              <Upload className="mr-2 h-4 w-4" />
              {isUploading ? "Загрузка" : "Загрузить"}
            </Button>
          </>
        }
      />

      {error ? (
        <SurfaceCard>
          <div className="text-sm text-destructive">{error}</div>
        </SurfaceCard>
      ) : null}

      <SurfaceCard title="Реестр" subtitle="Версии, статусы и владельцы стратегий">
        {isLoading ? (
          <LoadingState label="Загрузка стратегий" />
        ) : strategies.length === 0 ? (
          <EmptyState
            icon={<FileCode2 className="h-5 w-5" />}
            title="Стратегий нет"
            description="Загрузите Python-файл стратегии."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Название</TableHead>
                <TableHead>Ключ</TableHead>
                <TableHead>Lifecycle</TableHead>
                <TableHead>Validation</TableHead>
                <TableHead>Версия</TableHead>
                <TableHead>Обновлена</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {strategies.map((strategy) => (
                <TableRow key={String(strategy.id)}>
                  <TableCell>
                    <Link
                      href={`/strategies/${strategy.id}`}
                      className="font-medium text-foreground hover:text-accent"
                    >
                      {strategy.name || strategy.fileName}
                    </Link>
                    <div className="mt-1 text-xs text-muted-foreground">{strategy.fileName}</div>
                  </TableCell>
                  <TableCell className="font-mono text-xs">{strategy.strategyKey ?? "n/a"}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(strategy.lifecycleStatus)}>
                      {strategy.lifecycleStatus}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(strategy.status)}>{strategy.status}</Badge>
                  </TableCell>
                  <TableCell>{strategy.latestVersion ?? "n/a"}</TableCell>
                  <TableCell>{formatDate(strategy.updatedAt ?? strategy.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SurfaceCard>

      <SurfaceCard title="Шаблоны" subtitle="Системные starter-шаблоны">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {templates.map((template) => (
            <div
              key={String(template.id)}
              className="rounded-[18px] border border-border/70 bg-panel-subtle p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-foreground">{template.name}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{template.category}</div>
                </div>
                <Badge variant="secondary">{template.strategyType ?? "BACKTEST"}</Badge>
              </div>
              <pre className="mt-3 max-h-28 overflow-auto rounded-xl bg-background/60 p-3 text-xs text-muted-foreground">
                {JSON.stringify(template.defaultParameters, null, 2)}
              </pre>
            </div>
          ))}
        </div>
      </SurfaceCard>
    </div>
  );
}
