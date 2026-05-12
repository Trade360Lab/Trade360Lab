"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, FileCode2, RadioTower, Rocket, Settings } from "lucide-react";
import { useLanguage } from "@/components/language/language-provider";
import { cn } from "@/lib/utils";
import { LogoPlaceholder } from "@/components/shell/logo-placeholder";

const SHOW_DEPLOY = false;

export type NavItem = {
  label: string;
  labelEn?: string;
  href: string;
  iconSrc?: string;
  icon?: typeof Activity;
  gated?: boolean;
};

export const navItems: NavItem[] = [
  {
    label: "\u0413\u043b\u0430\u0432\u043d\u043e\u0435",
    href: "/workspace",
    iconSrc: "/icons/Home-4--Streamline-Core.svg",
  },
  {
    label: "\u0420\u0430\u0431\u043e\u0447\u0438\u0439 \u0441\u0442\u043e\u043b",
    href: "/desktop",
    iconSrc: "/icons/Screen-1--Streamline-Core.svg",
  },
  {
    label: "\u0414\u0430\u043d\u043d\u044b\u0435",
    href: "/data",
    iconSrc: "/icons/Database--Streamline-Core.svg",
  },
  {
    label: "\u0411\u044d\u043a\u0442\u0435\u0441\u0442\u044b",
    href: "/backtests",
    iconSrc: "/icons/Bag-Suitcase-1--Streamline-Core.svg",
  },
  {
    label: "\u0421\u0442\u0440\u0430\u0442\u0435\u0433\u0438\u0438",
    labelEn: "Strategies",
    href: "/strategies",
    icon: FileCode2,
  },
  {
    label: "\u041f\u0435\u0439\u043f\u0435\u0440",
    labelEn: "Paper",
    href: "/paper",
    icon: Activity,
  },
  {
    label: "\u041b\u0430\u0439\u0432",
    labelEn: "Live",
    href: "/live",
    icon: RadioTower,
  },
  {
    label: "\u0418\u0441\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u044f",
    href: "/research",
    iconSrc: "/icons/Pencil--Streamline-Core.svg",
  },
  {
    label: "\u0411\u043e\u0442\u044b",
    href: "/bots",
    iconSrc: "/bot.png",
  },
  {
    label: "\u0414\u0435\u043f\u043b\u043e\u0439",
    href: "/deploy",
    icon: Rocket,
    gated: true,
  },
  {
    label: "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438",
    href: "/settings",
    icon: Settings,
  },
];

export function Sidebar() {
  const pathname = usePathname();
  const { language } = useLanguage();

  return (
    <aside className="glass-panel flex h-screen w-[220px] shrink-0 flex-col border-r px-3 py-4">
      <div className="flex justify-center pb-4">
        <LogoPlaceholder />
      </div>
      <div className="flex flex-1 items-center">
        <nav className="flex w-full flex-col gap-1">
          {navItems
            .filter((item) => (item.gated ? SHOW_DEPLOY : true))
            .map((item) => {
              const Icon = item.icon;
              const isActive =
                pathname === item.href ||
                (item.href !== "/workspace" && pathname.startsWith(`${item.href}/`));
              const isTradingItem = ["/strategies", "/paper", "/live"].includes(item.href);
              const label = language === "en" && item.labelEn ? item.labelEn : item.label;

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "group flex items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-sm transition-all duration-300",
                    isActive
                      ? "border-[hsl(var(--primary)/0.34)] bg-[linear-gradient(135deg,hsl(var(--primary)/0.18),hsl(var(--accent)/0.1))] text-foreground shadow-[0_0_18px_hsl(var(--primary)/0.14)]"
                      : isTradingItem
                        ? "border-[#c7ee51]/24 bg-[#c7ee51]/8 text-[#c7ee51] hover:border-[#c7ee51]/40 hover:bg-[#c7ee51]/14"
                      : "text-muted-foreground hover:border-[hsl(var(--tl-border-1)/0.8)] hover:bg-[hsl(var(--tl-bg-2)/0.78)] hover:text-foreground"
                  )}
                >
                  {item.iconSrc ? (
                    <Image
                      src={item.iconSrc}
                      alt=""
                      width={16}
                      height={16}
                      className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:scale-110"
                      aria-hidden="true"
                    />
                  ) : Icon ? (
                    <Icon
                      className={cn(
                        "h-4 w-4 shrink-0 transition-transform duration-300 group-hover:scale-110",
                        isTradingItem && "text-[#c7ee51]"
                      )}
                    />
                  ) : null}
                  <span className="leading-none">{label}</span>
                </Link>
              );
            })}
        </nav>
      </div>
    </aside>
  );
}
