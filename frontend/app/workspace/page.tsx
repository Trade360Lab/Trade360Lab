"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DemoModeBadge } from "@/components/shared/demo-mode-badge";
import { SurfaceCard } from "@/components/shared/surface-card";
import { useRuns } from "@/features/runs/store/run-store";
import { datasetVersions } from "@/lib/demo-data/datasets";
import { projects } from "@/lib/demo-data/projects";

const CHART_WIDTH = 560;
const CHART_HEIGHT = 260;

const projectChartPlaceholders: Record<string, number[]> = {
  "proj-atlas": [24, 30, 28, 36, 33, 42, 48, 45, 52, 58],
  "proj-orbit": [38, 34, 29, 33, 31, 36, 41, 39, 44, 47],
  "proj-ridge": [20, 23, 27, 25, 30, 34, 32, 37, 40, 43],
};

function buildSparklinePath(values: number[], width: number, height: number) {
  if (values.length === 0) {
    return "";
  }

  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = max - min || 1;
  const stepX = width / Math.max(values.length - 1, 1);

  return values
    .map((value, index) => {
      const x = index * stepX;
      const y = height - ((value - min) / range) * (height - 18) - 10;
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)} ${y.toFixed(2)}`;
    })
    .join(" ");
}

export default function WorkspacePage() {
  const { runs } = useRuns();
  const router = useRouter();
  const runningRuns = runs.filter((run) => run.status === "running").length;
  const queuedRuns = runs.filter((run) => run.status === "queued").length;

  const [projectPage, setProjectPage] = useState(0);
  const [projectsPerView, setProjectsPerView] = useState(() =>
    typeof window !== "undefined" && window.innerWidth < 1024 ? 1 : 2
  );

  const projectTones = [
    {
      accent: "#33CC99",
      glow: "rgba(51, 204, 153, 0.32)",
      tint: "rgba(51, 204, 153, 0.16)",
    },
    {
      accent: "#6AA1FF",
      glow: "rgba(106, 161, 255, 0.3)",
      tint: "rgba(106, 161, 255, 0.16)",
    },
    {
      accent: "#E5A84A",
      glow: "rgba(229, 168, 74, 0.3)",
      tint: "rgba(229, 168, 74, 0.14)",
    },
  ];

  const summaryCards = [
    {
      label: "Проекты",
      value: projects.length,
      hint: "активные рабочие пространства",
      iconSrc: "/icons/Module-Puzzle-3--Streamline-Core.svg",
      iconAlt: "Иконка проектов",
    },
    {
      label: "Датасеты",
      value: datasetVersions.length,
      hint: "версии, готовые к запуску",
      iconSrc: "/icons/Database-Server-1--Streamline-Core.svg",
      iconAlt: "Иконка датасетов",
    },
    {
      label: "Запуски",
      value: runs.length,
      hint: `${runningRuns} выполняется, ${queuedRuns} в очереди`,
      iconSrc: "/icons/Desktop-Check--Streamline-Core.svg",
      iconAlt: "Иконка запусков",
    },
  ];

  const fxRates = [
    { pair: "USD/RUB", value: "90.24" },
    { pair: "EUR/RUB", value: "98.17" },
    { pair: "GBP/RUB", value: "115.84" },
    { pair: "CNY/RUB", value: "12.49" },
  ];

  useEffect(() => {
    const updateProjectsPerView = () => {
      setProjectsPerView(window.innerWidth < 1024 ? 1 : 2);
    };

    window.addEventListener("resize", updateProjectsPerView);
    return () => window.removeEventListener("resize", updateProjectsPerView);
  }, []);

  const maxProjectPage = Math.max(0, projects.length - projectsPerView);
  const normalizedProjectPage = Math.min(projectPage, maxProjectPage);
  const canGoPrev = normalizedProjectPage > 0;
  const canGoNext = normalizedProjectPage < maxProjectPage;

  const translateX = useMemo(
    () => `translateX(-${(normalizedProjectPage * 100) / projectsPerView}%)`,
    [normalizedProjectPage, projectsPerView]
  );

  return (
    <div className="flex min-h-full flex-col gap-5">
      <SurfaceCard>
        <div className="mb-4">
          <DemoModeBadge label="Demo data" />
        </div>
        <div className="relative">
          {canGoPrev ? (
            <Button
              variant="ghost"
              size="icon"
              className="absolute left-0 top-1/2 z-20 h-11 w-11 -translate-x-1/2 -translate-y-1/2 rounded-full border-0 bg-transparent text-foreground/70 shadow-none hover:bg-transparent hover:text-foreground"
              onClick={() =>
                setProjectPage((current) => Math.max(0, Math.min(current, maxProjectPage) - 1))
              }
            >
              <ChevronLeft className="h-5 w-5" />
            </Button>
          ) : null}
          {canGoNext ? (
            <Button
              variant="ghost"
              size="icon"
              className="absolute right-0 top-1/2 z-20 h-11 w-11 translate-x-1/2 -translate-y-1/2 rounded-full border-0 bg-transparent text-foreground/70 shadow-none hover:bg-transparent hover:text-foreground"
              onClick={() =>
                setProjectPage((current) =>
                  Math.min(maxProjectPage, Math.min(current, maxProjectPage) + 1)
                )
              }
            >
              <ChevronRight className="h-5 w-5" />
            </Button>
          ) : null}

          <div className="overflow-hidden">
            <div
              className="flex transition-transform duration-500 ease-out"
              style={{ transform: translateX }}
            >
              {projects.map((project, index) => {
                const tone = projectTones[index % projectTones.length];
                const chartValues = projectChartPlaceholders[project.id] ?? [
                  22, 26, 24, 31, 29, 35, 38, 41,
                ];
                const chartPath = buildSparklinePath(
                  chartValues,
                  CHART_WIDTH,
                  CHART_HEIGHT
                );
                const chartAreaPath = chartPath
                  ? `${chartPath} L ${CHART_WIDTH} ${CHART_HEIGHT} L 0 ${CHART_HEIGHT} Z`
                  : "";

                return (
                  <div
                    key={project.id}
                    className="shrink-0 px-2 first:pl-0 last:pr-0"
                    style={{ flex: `0 0 ${100 / projectsPerView}%` }}
                  >
                    <div className="group relative flex min-h-[430px] flex-col overflow-hidden rounded-[24px] border border-border/80 bg-[linear-gradient(150deg,hsl(var(--tl-bg-1)/0.98),hsl(var(--tl-bg-2)/0.95)_78%)] transition-all duration-300 hover:-translate-y-0.5 hover:border-border hover:shadow-[0_22px_44px_rgba(0,0,0,0.16)]">
                      <div
                        className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
                        style={{
                          background: `radial-gradient(560px 220px at -6% 18%, ${tone.tint}, rgba(20,24,35,0) 62%)`,
                        }}
                      />
                      <div className="pointer-events-none absolute inset-0 z-0 opacity-45">
                        <svg
                          viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                          preserveAspectRatio="none"
                          className="h-full w-full"
                        >
                          <defs>
                            <linearGradient
                              id={`project-chart-fill-${project.id}`}
                              x1="0"
                              y1="0"
                              x2="0"
                              y2="1"
                            >
                              <stop offset="0%" stopColor={`${tone.accent}55`} />
                              <stop offset="100%" stopColor={`${tone.accent}00`} />
                            </linearGradient>
                          </defs>
                          <path
                            d={`M 0 78 H ${CHART_WIDTH}`}
                            stroke="hsl(var(--tl-border-1) / 0.72)"
                            strokeDasharray="5 6"
                          />
                          <path
                            d={`M 0 152 H ${CHART_WIDTH}`}
                            stroke="hsl(var(--tl-border-1) / 0.55)"
                            strokeDasharray="5 6"
                          />
                          <path
                            d={chartAreaPath}
                            fill={`url(#project-chart-fill-${project.id})`}
                          />
                          <path
                            d={chartPath}
                            fill="none"
                            stroke={tone.accent}
                            strokeWidth="2.6"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            opacity="0.9"
                          />
                        </svg>
                      </div>
                      <div className="pointer-events-none absolute right-4 top-3 z-0 select-none text-[82px] font-semibold leading-none text-foreground/10">
                        {(index + 1).toString().padStart(2, "0")}
                      </div>
                      <div className="relative z-10 border-b border-border/70 px-4 py-4">
                        <div
                          className="pointer-events-none absolute inset-y-0 left-0 w-1"
                          style={{ backgroundColor: tone.accent }}
                        />
                        <div
                          className="pointer-events-none absolute -left-10 top-1/2 h-28 w-28 -translate-y-1/2 rounded-full blur-[28px]"
                          style={{ backgroundColor: tone.glow }}
                        />
                        <div className="relative mb-3 pl-3 pr-14">
                          <div className="text-lg font-semibold leading-tight text-foreground">
                            {project.name}
                          </div>
                          <div className="mt-1 text-sm text-muted-foreground">
                            {project.description}
                          </div>
                        </div>
                      </div>
                      <div className="relative z-10 flex flex-1 flex-col px-4 pb-0 pt-4">
                        <div className="grid grid-cols-2 gap-3">
                          <div className="flex flex-col gap-3">
                            <div className="rounded-xl border border-border/70 bg-[hsl(var(--tl-bg-1)/0.58)] px-3 py-3 text-xs">
                              <div className="mb-1 text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Последняя активность
                              </div>
                              <div className="text-foreground">{project.lastActive}</div>
                            </div>
                            <div className="rounded-xl border border-border/70 bg-[hsl(var(--tl-bg-1)/0.58)] px-3 py-3 text-xs">
                              <div className="mb-1 text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Датасет
                              </div>
                              <div className="text-foreground">{project.lastDataset}</div>
                            </div>
                          </div>
                          <div className="self-start rounded-xl border border-border/70 bg-[hsl(var(--tl-bg-1)/0.58)] px-3 py-3 text-xs">
                            <div className="mb-1 text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                              Запуски
                            </div>
                            <div className="text-foreground">{project.recentRuns.length}</div>
                          </div>
                        </div>
                        <div className="mt-auto pt-4">
                          <Button
                            variant="secondary"
                            className="-mx-4 h-11 w-[calc(100%+2rem)] rounded-none rounded-b-[24px] border-0 text-foreground/90 hover:brightness-105"
                            style={{
                              background: `linear-gradient(135deg, ${tone.accent}44, ${tone.accent}22)`,
                              boxShadow:
                                "inset 0 1px 0 rgba(255,255,255,0.06), 0 -8px 18px rgba(0,0,0,0.12)",
                            }}
                            onClick={() => router.push(`/desktop?project=${project.id}`)}
                          >
                            Открыть
                          </Button>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </SurfaceCard>

      <div className="rounded-[28px] border border-border/80 p-2">
        <div className="grid gap-2 md:grid-cols-3">
          {summaryCards.map((card) => (
            <SurfaceCard
              key={card.label}
              className="w-full rounded-[22px] border-border/80 bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.98),hsl(var(--tl-bg-2)/0.92))] shadow-none"
              contentClassName="p-0"
            >
              <div className="relative min-h-[112px] overflow-hidden">
                <Image
                  src={card.iconSrc}
                  alt={card.iconAlt}
                  width={172}
                  height={172}
                  className="pointer-events-none absolute -left-[58px] top-1/2 h-[172px] w-[172px] -translate-y-1/2 select-none opacity-25"
                />
                <div className="pointer-events-none absolute inset-y-0 left-0 w-[46%] bg-[linear-gradient(90deg,hsl(var(--tl-bg-0)/0.18),transparent)]" />
                <div className="relative ml-[34%] py-3 pl-3 pr-3">
                  <div className="text-[11px] uppercase tracking-[0.2em] text-muted-foreground">
                    {card.label}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">{card.hint}</div>
                  <div className="mt-3 text-3xl font-semibold text-foreground">
                    {card.value}
                  </div>
                </div>
              </div>
            </SurfaceCard>
          ))}
        </div>
      </div>

      <div className="rounded-[18px] border border-border/80 bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.98),hsl(var(--tl-bg-2)/0.92))] p-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
          <div className="shrink-0 border-b border-[hsl(var(--tl-glow)/0.45)] pb-2 text-[11px] uppercase tracking-[0.14em] text-muted-foreground lg:w-[180px] lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4">
            Курсы валют
          </div>
          <div className="grid min-w-0 flex-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
            {fxRates.map((rate) => (
              <div
                key={rate.pair}
                className="flex min-w-0 items-center justify-between rounded-[14px] border border-[hsl(var(--tl-glow)/0.24)] bg-[linear-gradient(160deg,hsl(var(--tl-glow)/0.14),hsl(var(--tl-bg-1)/0.74))] px-4 py-3"
              >
                <div className="text-[11px] uppercase tracking-[0.1em] text-muted-foreground">
                  {rate.pair}
                </div>
                <div className="font-mono text-base font-semibold text-foreground">{rate.value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
