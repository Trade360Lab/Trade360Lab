"use client";

import { ReactNode, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useLanguage } from "@/components/language/language-provider";
import { useAuth } from "@/features/auth/auth-provider";
import { Topbar } from "@/components/shell/topbar";
import { Sidebar } from "@/components/shell/sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isReady } = useAuth();
  const { language } = useLanguage();
  const isAuthPage = pathname === "/login" || pathname === "/register";
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);

  useEffect(() => {
    if (!isReady || isAuthPage || isAuthenticated) {
      return;
    }
    router.replace("/login");
  }, [isAuthenticated, isAuthPage, isReady, router]);

  if (isAuthPage) {
    return <>{children}</>;
  }

  if (!isReady || !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-sm text-muted-foreground">
        {language === "en" ? "Loading session..." : "Загрузка сессии..."}
      </div>
    );
  }

  return (
    <div className="relative isolate h-screen min-h-screen w-full overflow-hidden bg-[var(--tl-body-solid)] text-foreground">
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden bg-[linear-gradient(145deg,var(--tl-body-gradient-top)_0%,var(--tl-body-solid)_48%,var(--tl-body-gradient-bottom)_100%)]">
        <div className="absolute left-[-12%] top-[-20%] h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,hsl(var(--tl-glow)/0.14)_0%,hsl(var(--tl-glow-soft)/0.07)_42%,transparent_72%)] blur-2xl" />
        <div className="absolute right-[-16%] top-[-18%] h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,hsl(var(--tl-glow-soft)/0.1)_0%,hsl(var(--tl-glow)/0.05)_38%,transparent_76%)] blur-2xl" />
        <div className="absolute inset-0 bg-[linear-gradient(to_right,var(--tl-grid-line-x)_1px,transparent_1px),linear-gradient(to_bottom,var(--tl-grid-line-y)_1px,transparent_1px)] bg-[size:96px_96px] opacity-35 [mask-image:linear-gradient(180deg,black,transparent_82%)]" />
      </div>

      {isMobileSidebarOpen ? (
        <div
          className="fixed inset-0 z-40 bg-black/62 backdrop-blur-sm md:hidden"
          onClick={() => setIsMobileSidebarOpen(false)}
        />
      ) : null}
      {isMobileSidebarOpen ? (
        <div className="fixed inset-y-3 left-3 z-50 w-[286px] md:hidden">
          <Sidebar
            variant="mobile"
            onNavigate={() => setIsMobileSidebarOpen(false)}
            onClose={() => setIsMobileSidebarOpen(false)}
            className="translate-x-0"
          />
        </div>
      ) : null}

      <div className="mx-auto flex h-full w-full max-w-[1680px] flex-col p-2 sm:p-3 lg:p-5">
        <div className="relative flex h-full min-h-0 flex-1 overflow-hidden rounded-[28px] border border-[hsl(var(--tl-border-1)/0.58)] bg-[linear-gradient(135deg,hsl(var(--tl-bg-1)/0.88),hsl(var(--tl-bg-0)/0.96)_52%,hsl(var(--tl-bg-0)/0.98))] shadow-[inset_0_1px_0_hsl(var(--tl-glass-highlight)/0.09),0_24px_64px_var(--tl-shell-shadow)] lg:rounded-[34px]">
          <div className="pointer-events-none absolute inset-0 rounded-[inherit] ring-1 ring-inset ring-white/5" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-[linear-gradient(180deg,rgba(255,255,255,0.055),transparent)]" />
          <div className="pointer-events-none absolute bottom-0 right-0 h-72 w-72 bg-[radial-gradient(circle,hsl(var(--tl-glow)/0.07)_0%,transparent_68%)] blur-xl" />

          <div className="hidden min-h-0 w-[264px] shrink-0 md:block xl:w-[276px]">
            <Sidebar />
          </div>

          <section className="relative flex min-w-0 flex-1 flex-col border-l border-[hsl(var(--tl-border-1)/0.4)] bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.74),hsl(var(--tl-bg-0)/0.68))]">
            <Topbar onOpenSidebar={() => setIsMobileSidebarOpen(true)} />
            <main className="relative flex min-h-0 flex-1 overflow-hidden">
              <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_46%_0%,hsl(var(--tl-glow)/0.07),transparent_42%)]" />
              <div className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden md:p-5 xl:p-6">
                {children}
              </div>
            </main>
          </section>
        </div>
      </div>
    </div>
  );
}
