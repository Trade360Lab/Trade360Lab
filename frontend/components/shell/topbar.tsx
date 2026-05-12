"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Github, Languages, LogOut, Mail, Settings2 } from "lucide-react";
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

export function Topbar() {
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
  };
  const primaryNavItems = navItems.filter(
    (item) => !item.gated && item.href !== "/settings"
  );
  const isSettingsActive =
    pathname === "/settings" || pathname.startsWith("/settings/");

  return (
    <header className="relative z-20 border-b border-[hsl(var(--tl-border-1)/0.48)] bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.98),hsl(var(--tl-bg-0)/0.98))] px-4 py-4 md:px-6">
      <div className="flex items-center gap-5">
        <Link
          href="/workspace"
          className="group flex shrink-0 items-center px-1 py-1 transition-opacity duration-300 hover:opacity-90"
          aria-label="TradeLab home"
        >
          <Image
            src="/Logo.png"
            alt="TradeLab logo"
            width={240}
            height={90}
            className="h-[50px] w-auto origin-left scale-[1.15] object-contain md:h-[55px]"
            priority
          />
        </Link>

        <nav className="mx-auto flex min-w-0 flex-1 items-center justify-start gap-2 overflow-x-auto px-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden lg:justify-center">
          {primaryNavItems.map((item) => {
            const Icon = item.icon;
            const isActive =
              pathname === item.href ||
              (item.href !== "/workspace" && pathname.startsWith(`${item.href}/`));
            const label = isEnglish && item.labelEn ? item.labelEn : item.label;

            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "group flex h-10 shrink-0 items-center gap-2.5 rounded-full border px-4 text-[15px] font-semibold text-muted-foreground transition-all duration-200",
                  isActive
                    ? "border-[#c7ee51]/40 bg-[#c7ee51]/14 text-[#c7ee51] shadow-[inset_0_1px_0_rgba(255,255,255,0.08)]"
                    : "border-transparent bg-transparent hover:border-[hsl(var(--tl-border-1)/0.46)] hover:bg-[hsl(var(--tl-bg-2)/0.78)] hover:text-foreground"
                )}
              >
                {item.iconSrc ? (
                  <Image
                    src={item.iconSrc}
                    alt=""
                    width={18}
                    height={18}
                    className="h-[18px] w-[18px] shrink-0"
                    aria-hidden="true"
                  />
                ) : Icon ? (
                    <Icon
                      className="h-[18px] w-[18px] shrink-0 text-[#c7ee51]"
                    />
                ) : null}
                <span className="leading-none">{label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-2.5">
          <a
            href="https://t.me/trading360l"
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200 hover:border-[hsl(var(--tl-border-1)/0.52)] hover:bg-[hsl(var(--tl-bg-2)/0.82)] hover:text-foreground"
            aria-label="Telegram"
          >
            <Image
              src="/icons/Telegram--Streamline-Core.svg"
              alt=""
              width={24}
              height={24}
              className="h-6 w-6 shrink-0"
              aria-hidden="true"
            />
          </a>
          <a
            href="https://github.com/AlexToday111/TradeLab.git"
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200 hover:border-[hsl(var(--tl-border-1)/0.52)] hover:bg-[hsl(var(--tl-bg-2)/0.82)] hover:text-foreground"
            aria-label="GitHub"
          >
            <Github className="h-5 w-5" />
          </a>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200 hover:border-[hsl(var(--tl-border-1)/0.52)] hover:bg-[hsl(var(--tl-bg-2)/0.82)] hover:text-foreground"
                aria-label={text.interfaceSettings}
              >
                <Image
                  src="/icons/settings.svg"
                  alt=""
                  width={20}
                  height={20}
                  className="h-5 w-5 shrink-0"
                  aria-hidden="true"
                />
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
          {isSettingsActive ? (
            <Link
              href="/settings"
              className="inline-flex h-10 items-center rounded-[12px] border border-[hsl(var(--primary)/0.18)] bg-[hsl(var(--primary)/0.08)] px-4 text-[14px] font-medium text-foreground"
            >
              <Settings2 className="mr-2 h-[18px] w-[18px]" />
              {text.settings}
            </Link>
          ) : null}
        </div>
      </div>
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-[linear-gradient(90deg,transparent,rgba(111,247,163,0.14),rgba(43,213,118,0.18),transparent)]" />
    </header>
  );
}
