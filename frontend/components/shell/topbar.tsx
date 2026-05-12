"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Github, Mail, Menu, Search, Send } from "lucide-react";
import { useLanguage } from "@/components/language/language-provider";
import { useAuth } from "@/features/auth/auth-provider";
import { navItems } from "@/components/shell/sidebar";

type TopbarProps = {
  onOpenSidebar: () => void;
};

export function Topbar({ onOpenSidebar }: TopbarProps) {
  const pathname = usePathname();
  const { language } = useLanguage();
  const { session } = useAuth();
  const isEnglish = language === "en";
  const text = {
    anonymous: isEnglish ? "Not signed in" : "Не авторизован",
    openNavigation: isEnglish ? "Open navigation" : "Открыть навигацию",
    search: isEnglish ? "Search..." : "Поиск...",
    workspace: isEnglish ? "Workspace" : "Рабочая область",
  };
  const activeItem = navItems.find(
    (item) => pathname === item.href || (item.href !== "/workspace" && pathname.startsWith(`${item.href}/`))
  );
  const pageTitle = activeItem
    ? isEnglish && activeItem.labelEn
      ? activeItem.labelEn
      : activeItem.label
    : text.workspace;

  return (
    <header className="relative z-20 border-b border-[hsl(var(--tl-border-1)/0.42)] bg-[linear-gradient(180deg,rgba(15,18,28,0.82),rgba(9,12,18,0.58))] px-4 py-3 backdrop-blur-2xl md:px-5">
      <div className="flex min-h-12 items-center gap-3">
        <button
          type="button"
          onClick={onOpenSidebar}
          className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-white/[0.08] bg-white/[0.035] text-muted-foreground transition-colors hover:bg-white/[0.07] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 md:hidden"
          aria-label={text.openNavigation}
        >
          <Menu className="h-5 w-5" />
        </button>

        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2 text-[12px] text-muted-foreground">
            <Link
              href="/workspace"
              className="transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
            >
              Trade360Lab
            </Link>
            <span className="text-muted-foreground/45">/</span>
            <span className="truncate text-foreground">{pageTitle}</span>
          </div>
          <div className="mt-0.5 truncate text-lg font-semibold leading-tight text-foreground md:text-xl">
            {pageTitle}
          </div>
        </div>

        <div className="hidden min-w-[220px] max-w-[320px] flex-1 items-center gap-2 rounded-2xl border border-white/[0.07] bg-black/20 px-3 py-2 text-sm text-muted-foreground shadow-[inset_0_1px_0_rgba(255,255,255,0.04)] lg:flex">
          <Search className="h-4 w-4 shrink-0" />
          <span className="truncate">{text.search}</span>
        </div>

        <div className="ml-auto flex shrink-0 items-center gap-2">
          <div className="hidden max-w-[210px] items-center gap-2 rounded-2xl border border-white/[0.07] bg-white/[0.035] px-3 py-2 text-xs text-muted-foreground lg:flex">
            <Mail className="h-3.5 w-3.5 shrink-0" />
            <span className="truncate">{session?.user.email ?? text.anonymous}</span>
          </div>
          <a
            href="https://t.me/trading360l"
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-transparent text-muted-foreground transition-colors hover:border-white/[0.08] hover:bg-white/[0.055] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
            aria-label="Telegram"
          >
            <Send className="h-4 w-4" />
          </a>
          <a
            href="https://github.com/AlexToday111/TradeLab.git"
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-transparent text-muted-foreground transition-colors hover:border-white/[0.08] hover:bg-white/[0.055] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
            aria-label="GitHub"
          >
            <Github className="h-4 w-4" />
          </a>
        </div>
      </div>
    </header>
  );
}
