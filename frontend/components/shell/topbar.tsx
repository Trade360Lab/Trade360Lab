"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Github,
  Languages,
  LogOut,
  Mail,
  Menu,
  Search,
  Settings2,
  Send,
} from "lucide-react";
import { DemoModeBadge } from "@/components/shared/demo-mode-badge";
import { interfaceLanguageOptions, useLanguage } from "@/components/language/language-provider";
import { interfaceThemeOptions, useTheme } from "@/components/theme/theme-provider";
import { useAuth } from "@/features/auth/auth-provider";
import { navItems } from "@/components/shell/sidebar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

type TopbarProps = {
  onOpenSidebar: () => void;
};

export function Topbar({ onOpenSidebar }: TopbarProps) {
  const pathname = usePathname();
  const { theme, setTheme } = useTheme();
  const { language, setLanguage } = useLanguage();
  const { session, logout } = useAuth();
  const isEnglish = language === "en";
  const text = {
    settings: isEnglish ? "Settings" : "Настройки",
    interfaceSettings: isEnglish ? "Interface settings" : "Настройки интерфейса",
    account: isEnglish ? "Account" : "Аккаунт",
    anonymous: isEnglish ? "Not signed in" : "Не авторизован",
    theme: isEnglish ? "Interface theme" : "Тема интерфейса",
    darkTheme: isEnglish ? "Dark" : "Тёмная",
    lightTheme: isEnglish ? "Light" : "Светлая",
    language: isEnglish ? "Interface language" : "Язык интерфейса",
    logout: isEnglish ? "Logout" : "Выйти",
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
          <div className="hidden xl:block">
            <DemoModeBadge />
          </div>
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
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-transparent text-muted-foreground transition-colors hover:border-white/[0.08] hover:bg-white/[0.055] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
                aria-label={text.interfaceSettings}
              >
                <Settings2 className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="end"
              sideOffset={10}
              className="w-[320px] border-[hsl(var(--primary)/0.16)] bg-[linear-gradient(160deg,hsl(var(--popover)/0.98),hsl(var(--tl-bg-2)/0.95))] p-2"
            >
              <DropdownMenuLabel className="px-3 pt-2 text-base text-foreground">
                {text.settings}
              </DropdownMenuLabel>
              <div className="mx-1 mb-2 mt-1 rounded-[12px] border border-[hsl(var(--tl-border-1)/0.52)] bg-[hsl(var(--tl-bg-2)/0.72)] px-3 py-2.5">
                <div className="mb-1 flex items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                  <Mail className="h-3.5 w-3.5" />
                  {text.account}
                </div>
                <div className="truncate text-sm font-medium text-foreground">
                  {session?.user.email ?? text.anonymous}
                </div>
              </div>
              <DropdownMenuSeparator className="bg-border/80" />
              <div className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                {text.theme}
              </div>
              <div className="space-y-1 px-1 pb-1">
                {interfaceThemeOptions.map((option) => {
                  const isSelected = option.value === theme;

                  return (
                    <DropdownMenuItem
                      key={option.value}
                      disabled={option.disabled}
                      onSelect={(event) => {
                        if (option.disabled) {
                          event.preventDefault();
                          return;
                        }

                        setTheme(option.value);
                      }}
                      className={cn(
                        "rounded-[12px] px-3 py-2.5 text-sm font-medium focus:bg-[hsl(var(--tl-bg-2)/0.9)]",
                        option.disabled
                          ? "cursor-default text-muted-foreground/40"
                          : "cursor-pointer text-foreground/80",
                        isSelected &&
                          "bg-[#c9ef4e]/12 text-[#C9EF4E] focus:bg-[#c9ef4e]/12 focus:text-[#C9EF4E]"
                      )}
                    >
                      <span>{option.value === "black" ? text.darkTheme : text.lightTheme}</span>
                    </DropdownMenuItem>
                  );
                })}
              </div>
              <DropdownMenuSeparator className="bg-border/80" />
              <div className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                {text.language}
              </div>
              <div className="grid grid-cols-2 gap-1 px-1 pb-1">
                {interfaceLanguageOptions.map((option) => {
                  const isSelected = option.value === language;

                  return (
                    <DropdownMenuItem
                      key={option.value}
                      onSelect={() => setLanguage(option.value)}
                      className={cn(
                        "justify-center rounded-[12px] px-3 py-2.5 text-sm font-semibold focus:bg-[hsl(var(--tl-bg-2)/0.9)]",
                        isSelected
                          ? "bg-[#c9ef4e]/12 text-[#C9EF4E] focus:bg-[#c9ef4e]/12 focus:text-[#C9EF4E]"
                          : "cursor-pointer text-foreground/80"
                      )}
                    >
                      <Languages className="h-4 w-4" />
                      {option.label}
                    </DropdownMenuItem>
                  );
                })}
              </div>
              <DropdownMenuSeparator className="bg-border/80" />
              <DropdownMenuItem
                onSelect={logout}
                className="mx-1 cursor-pointer rounded-[12px] px-3 py-2.5 text-sm font-medium text-muted-foreground focus:bg-status-error/10 focus:text-status-error"
              >
                <LogOut className="h-4 w-4" />
                {text.logout}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <button
            type="button"
            onClick={logout}
            className="hidden h-10 items-center gap-2 rounded-2xl border border-transparent px-3 text-sm text-muted-foreground transition-colors hover:border-status-error/20 hover:bg-status-error/10 hover:text-status-error focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-status-error/50 sm:inline-flex"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden xl:inline">{text.logout}</span>
          </button>
        </div>
      </div>
    </header>
  );
}
