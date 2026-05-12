"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  Bot,
  ChevronLeft,
  Database,
  FileCode2,
  HelpCircle,
  Home,
  Languages,
  Laptop,
  LogOut,
  Mail,
  RadioTower,
  Rocket,
  Settings,
  type LucideIcon,
} from "lucide-react";
import { useLanguage } from "@/components/language/language-provider";
import { interfaceLanguageOptions } from "@/components/language/language-provider";
import { interfaceThemeOptions, useTheme } from "@/components/theme/theme-provider";
import { useAuth } from "@/features/auth/auth-provider";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

const SHOW_DEPLOY = false;

type NavGroup = "overview" | "research" | "automation" | "system";

export type NavItem = {
  label: string;
  labelEn?: string;
  href: string;
  icon: LucideIcon;
  group: NavGroup;
  gated?: boolean;
};

type SidebarProps = {
  variant?: "desktop" | "mobile";
  className?: string;
  onNavigate?: () => void;
  onClose?: () => void;
};

const navGroups: Array<{
  key: NavGroup;
  label: string;
  labelEn: string;
}> = [
  { key: "overview", label: "Обзор", labelEn: "Overview" },
  { key: "research", label: "Лаборатория", labelEn: "Trading Lab" },
  { key: "automation", label: "Автоматизация", labelEn: "Automation" },
  { key: "system", label: "Система", labelEn: "System" },
];

export const navItems: NavItem[] = [
  {
    label: "\u0413\u043b\u0430\u0432\u043d\u043e\u0435",
    labelEn: "Home",
    href: "/workspace",
    icon: Home,
    group: "overview",
  },
  {
    label: "\u0420\u0430\u0431\u043e\u0447\u0438\u0439 \u0441\u0442\u043e\u043b",
    labelEn: "Workspace",
    href: "/desktop",
    icon: Laptop,
    group: "overview",
  },
  {
    label: "\u0414\u0430\u043d\u043d\u044b\u0435",
    labelEn: "Data",
    href: "/data",
    icon: Database,
    group: "research",
  },
  {
    label: "\u0411\u044d\u043a\u0442\u0435\u0441\u0442\u044b",
    labelEn: "Backtests",
    href: "/backtests",
    icon: Activity,
    group: "research",
  },
  {
    label: "\u0421\u0442\u0440\u0430\u0442\u0435\u0433\u0438\u0438",
    labelEn: "Strategies",
    href: "/strategies",
    icon: FileCode2,
    group: "research",
  },
  {
    label: "\u041f\u0435\u0439\u043f\u0435\u0440",
    labelEn: "Paper",
    href: "/paper",
    icon: Activity,
    group: "automation",
  },
  {
    label: "\u041b\u0430\u0439\u0432",
    labelEn: "Live",
    href: "/live",
    icon: RadioTower,
    group: "automation",
  },
  {
    label: "\u0411\u043e\u0442\u044b",
    labelEn: "Bots",
    href: "/bots",
    icon: Bot,
    group: "automation",
  },
  {
    label: "\u0414\u0435\u043f\u043b\u043e\u0439",
    labelEn: "Deploy",
    href: "/deploy",
    icon: Rocket,
    group: "system",
    gated: true,
  },
  {
    label: "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438",
    labelEn: "Settings",
    href: "/settings",
    icon: Settings,
    group: "system",
    gated: true,
  },
];

export function Sidebar({
  variant = "desktop",
  className,
  onNavigate,
  onClose,
}: SidebarProps) {
  const pathname = usePathname();
  const { theme, setTheme } = useTheme();
  const { language, setLanguage } = useLanguage();
  const { session, logout } = useAuth();
  const isEnglish = language === "en";
  const displayName = session?.user.email?.split("@")[0] ?? (isEnglish ? "Trader" : "Трейдер");
  const accountLabel = session?.user.email ?? (isEnglish ? "Signed in workspace" : "Рабочая сессия активна");
  const settingsText = {
    settings: isEnglish ? "Settings" : "Настройки",
    account: isEnglish ? "Account" : "Аккаунт",
    anonymous: isEnglish ? "Not signed in" : "Не авторизован",
    theme: isEnglish ? "Interface theme" : "Тема интерфейса",
    darkTheme: isEnglish ? "Dark" : "Тёмная",
    lightTheme: isEnglish ? "Light" : "Светлая",
    language: isEnglish ? "Interface language" : "Язык интерфейса",
  };

  return (
    <aside
      className={cn(
        "relative flex h-full min-h-0 flex-col overflow-hidden border-r border-[hsl(var(--tl-border-1)/0.44)] bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.96),hsl(var(--tl-bg-0)/0.94))] shadow-[inset_1px_0_0_rgba(255,255,255,0.05),inset_-1px_0_0_rgba(255,255,255,0.035)] backdrop-blur-md transition-transform duration-300",
        variant === "mobile" && "rounded-[26px] border border-[hsl(var(--tl-border-1)/0.58)] shadow-[0_24px_80px_rgba(0,0,0,0.52)]",
        className
      )}
    >
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_24%_0%,hsl(var(--tl-glow)/0.12),transparent_38%),radial-gradient(circle_at_70%_16%,hsl(var(--tl-glow-soft)/0.08),transparent_34%)]" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_bottom,rgba(255,255,255,0.045),transparent_28%)]" />

      <div className="relative flex min-h-0 flex-1 flex-col px-4 py-4">
        <div className="flex items-center gap-3 border-b border-[hsl(var(--tl-border-1)/0.44)] pb-4">
          <Link
            href="/workspace"
            onClick={onNavigate}
            className="flex min-w-0 flex-1 items-center rounded-2xl p-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
            aria-label={isEnglish ? "Open Trade360Lab home" : "Открыть главную Trade360Lab"}
          >
            <Image
              src="/Logo.png"
              alt="Trade360Lab"
              width={190}
              height={72}
              className="h-12 w-auto object-contain"
              priority
            />
          </Link>
          {variant === "mobile" ? (
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-white/[0.04] text-muted-foreground transition-colors hover:bg-white/[0.08] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
              aria-label={isEnglish ? "Close navigation" : "Закрыть навигацию"}
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          ) : null}
        </div>

        <div className="relative mt-4 overflow-hidden rounded-[22px] border border-white/[0.07] bg-[linear-gradient(145deg,rgba(255,255,255,0.055),rgba(255,255,255,0.018))] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]">
          <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.18),transparent)]" />
          <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
            {isEnglish ? "Welcome Back" : "С возвращением"}
          </div>
          <div className="mt-2 truncate text-xl font-semibold leading-tight text-foreground">
            {displayName}
          </div>
          <div className="mt-1 truncate text-xs text-muted-foreground">{accountLabel}</div>
        </div>

        <nav className="mt-4 min-h-0 flex-1 overflow-y-auto pr-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <div className="space-y-5 pb-5">
            {navGroups.map((group) => {
              const items = navItems.filter(
                (item) => item.group === group.key && (item.gated ? SHOW_DEPLOY : true)
              );

              if (items.length === 0) {
                return null;
              }

              return (
                <SidebarSection
                  key={group.key}
                  label={isEnglish ? group.labelEn : group.label}
                  items={items}
                  pathname={pathname}
                  isEnglish={isEnglish}
                  onNavigate={onNavigate}
                />
              );
            })}
          </div>
        </nav>

        <div className="relative shrink-0 border-t border-[hsl(var(--tl-border-1)/0.44)] pt-4">
          <a
            href="https://t.me/trading360l"
            target="_blank"
            rel="noreferrer"
            className="mb-2 flex items-center gap-3 rounded-[16px] border border-transparent px-3 py-2.5 text-sm text-muted-foreground transition-colors hover:border-white/[0.08] hover:bg-white/[0.04] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
          >
            <HelpCircle className="h-4 w-4" />
            <span>{isEnglish ? "Support" : "Поддержка"}</span>
          </a>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="mb-2 flex w-full items-center gap-3 rounded-[16px] border border-transparent px-3 py-2.5 text-left text-sm text-muted-foreground transition-colors hover:border-white/[0.08] hover:bg-white/[0.04] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
              >
                <Settings className="h-4 w-4" />
                <span>{settingsText.settings}</span>
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="start"
              side="right"
              sideOffset={12}
              className="w-[320px] border-[hsl(var(--primary)/0.16)] bg-[linear-gradient(160deg,hsl(var(--popover)/0.98),hsl(var(--tl-bg-2)/0.95))] p-2"
            >
              <DropdownMenuLabel className="px-3 pt-2 text-base text-foreground">
                {settingsText.settings}
              </DropdownMenuLabel>
              <div className="mx-1 mb-2 mt-1 rounded-[12px] border border-[hsl(var(--tl-border-1)/0.52)] bg-[hsl(var(--tl-bg-2)/0.72)] px-3 py-2.5">
                <div className="mb-1 flex items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                  <Mail className="h-3.5 w-3.5" />
                  {settingsText.account}
                </div>
                <div className="truncate text-sm font-medium text-foreground">
                  {session?.user.email ?? settingsText.anonymous}
                </div>
              </div>
              <DropdownMenuSeparator className="bg-border/80" />
              <div className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                {settingsText.theme}
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
                      <span>{option.value === "black" ? settingsText.darkTheme : settingsText.lightTheme}</span>
                    </DropdownMenuItem>
                  );
                })}
              </div>
              <DropdownMenuSeparator className="bg-border/80" />
              <div className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-[0.22em] text-muted-foreground/80">
                {settingsText.language}
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
            </DropdownMenuContent>
          </DropdownMenu>
          <button
            type="button"
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-[16px] border border-transparent px-3 py-2.5 text-left text-sm text-muted-foreground transition-colors hover:border-status-error/20 hover:bg-status-error/10 hover:text-status-error focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-status-error/50"
          >
            <LogOut className="h-4 w-4" />
            <span>{isEnglish ? "Logout" : "Выйти"}</span>
          </button>
        </div>
      </div>
    </aside>
  );
}

function SidebarSection({
  label,
  items,
  pathname,
  isEnglish,
  onNavigate,
}: {
  label: string;
  items: NavItem[];
  pathname: string;
  isEnglish: boolean;
  onNavigate?: () => void;
}) {
  return (
    <section>
      <div className="mb-2 px-3 text-[11px] font-medium uppercase tracking-[0.18em] text-muted-foreground/72">
        {label}
      </div>
      <div className="space-y-1">
        {items.map((item) => (
          <SidebarNavItem
            key={item.href}
            item={item}
            isActive={pathname === item.href || (item.href !== "/workspace" && pathname.startsWith(`${item.href}/`))}
            label={isEnglish && item.labelEn ? item.labelEn : item.label}
            onNavigate={onNavigate}
          />
        ))}
      </div>
    </section>
  );
}

function SidebarNavItem({
  item,
  isActive,
  label,
  onNavigate,
}: {
  item: NavItem;
  isActive: boolean;
  label: string;
  onNavigate?: () => void;
}) {
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      className={cn(
        "group relative flex items-center gap-3 overflow-hidden rounded-[16px] border px-3 py-2.5 text-sm transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70",
        isActive
          ? "border-[#c7ee51]/32 bg-[#c7ee51]/12 text-[#c7ee51] shadow-[inset_0_1px_0_rgba(255,255,255,0.1),0_12px_32px_rgba(43,213,118,0.12)]"
          : "border-transparent text-muted-foreground hover:border-[#c7ee51]/20 hover:bg-[#c7ee51]/8 hover:text-foreground"
      )}
    >
      <span
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border transition-colors",
          isActive
            ? "border-[#c7ee51]/24 bg-[#c7ee51]/14 text-[#c7ee51]"
            : "border-white/[0.06] bg-white/[0.025] text-muted-foreground group-hover:text-[#c7ee51]"
        )}
      >
        <Icon className="h-4 w-4" />
      </span>
      <span className="truncate font-medium">{label}</span>
      {isActive ? (
        <>
          <span className="pointer-events-none absolute inset-x-4 top-0 h-px bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.28),transparent)]" />
          <span className="absolute right-2 top-1/2 h-7 w-1 -translate-y-1/2 rounded-full bg-[#c7ee51] shadow-[0_0_18px_rgba(199,238,81,0.5)]" />
        </>
      ) : null}
    </Link>
  );
}
