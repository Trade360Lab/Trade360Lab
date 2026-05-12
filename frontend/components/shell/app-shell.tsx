"use client";

import { ReactNode, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/auth-provider";
import { Topbar } from "@/components/shell/topbar";
import { Sidebar } from "@/components/shell/sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, isReady } = useAuth();
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
        Loading session...
      </div>
    );
  }

  return (
    <div className="relative isolate h-screen min-h-screen w-full overflow-hidden bg-[var(--tl-body-solid)] text-foreground">
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden bg-[linear-gradient(145deg,#101522_0%,var(--tl-body-solid)_42%,#05070c_100%)]">
        <div className="absolute left-[-14%] top-[-22%] h-[560px] w-[560px] rounded-full bg-[radial-gradient(circle,rgba(118,118,170,0.36)_0%,rgba(43,213,118,0.12)_38%,rgba(43,213,118,0)_72%)] blur-3xl" />
        <div className="absolute right-[-18%] top-[-20%] h-[620px] w-[620px] rounded-full bg-[radial-gradient(circle,rgba(43,213,118,0.18)_0%,rgba(111,247,163,0.08)_34%,rgba(43,213,118,0)_76%)] blur-3xl" />
        <div className="absolute bottom-[-28%] left-[28%] h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,rgba(43,213,118,0.1)_0%,rgba(43,213,118,0)_74%)] blur-3xl" />
        <div className="absolute inset-0 bg-[linear-gradient(to_right,rgba(148,163,184,0.04)_1px,transparent_1px),linear-gradient(to_bottom,rgba(148,163,184,0.025)_1px,transparent_1px)] bg-[size:72px_72px] opacity-60 [mask-image:radial-gradient(circle_at_50%_28%,black,transparent_78%)]" />
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
        <div className="relative flex h-full min-h-0 flex-1 overflow-hidden rounded-[28px] border border-[hsl(var(--tl-border-1)/0.58)] bg-[linear-gradient(135deg,hsl(var(--tl-bg-1)/0.82),hsl(var(--tl-bg-0)/0.94)_48%,rgba(6,8,14,0.98))] shadow-[inset_0_1px_0_hsl(var(--tl-glass-highlight)/0.09),0_28px_80px_rgba(0,0,0,0.44)] backdrop-blur-2xl lg:rounded-[34px]">
          <div className="pointer-events-none absolute inset-0 rounded-[inherit] ring-1 ring-inset ring-white/5" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-[linear-gradient(180deg,rgba(255,255,255,0.055),transparent)]" />
          <div className="pointer-events-none absolute bottom-0 right-0 h-80 w-80 bg-[radial-gradient(circle,rgba(43,213,118,0.09)_0%,transparent_68%)] blur-2xl" />

          <div className="hidden min-h-0 w-[264px] shrink-0 md:block xl:w-[276px]">
            <Sidebar />
          </div>

          <section className="relative flex min-w-0 flex-1 flex-col border-l border-[hsl(var(--tl-border-1)/0.4)] bg-[linear-gradient(180deg,rgba(11,14,22,0.7),rgba(6,8,13,0.64))]">
            <Topbar onOpenSidebar={() => setIsMobileSidebarOpen(true)} />
            <main className="relative flex min-h-0 flex-1 overflow-hidden">
              <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_46%_0%,rgba(43,213,118,0.1),transparent_46%)]" />
              <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,rgba(148,163,184,0.025)_1px,transparent_1px),linear-gradient(to_bottom,rgba(148,163,184,0.018)_1px,transparent_1px)] bg-[size:86px_86px] opacity-60 [mask-image:linear-gradient(180deg,black,transparent_88%)]" />
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
