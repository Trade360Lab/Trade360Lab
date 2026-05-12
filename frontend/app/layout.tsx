import type { Metadata } from "next";
import "./globals.css";
import { AppShell } from "@/components/shell/app-shell";
import { Providers } from "@/app/providers";

export const metadata: Metadata = {
  title: "Trade360Lab",
  description: "Интерфейс исследования, запуска и сравнения торговых сценариев.",
  icons: {
    icon: "/Favicon.svg",
    shortcut: "/Favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru" data-theme="neon" suppressHydrationWarning>
      <body className="bg-background text-foreground">
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
