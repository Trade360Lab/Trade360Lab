"use client";

import { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { LanguageProvider } from "@/components/language/language-provider";
import { ThemeProvider } from "@/components/theme/theme-provider";
import { AuthProvider, useAuth } from "@/features/auth/auth-provider";
import { RunStoreProvider } from "@/features/runs/store/run-store";

function ScopedProviders({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { isAuthenticated } = useAuth();
  const isAuthPage = pathname === "/login" || pathname === "/register";

  if (isAuthPage || !isAuthenticated) {
    return <>{children}</>;
  }

  return <RunStoreProvider>{children}</RunStoreProvider>;
}

export function Providers({ children }: { children: ReactNode }) {
  return (
    <LanguageProvider>
      <ThemeProvider>
        <AuthProvider>
          <ScopedProviders>{children}</ScopedProviders>
        </AuthProvider>
      </ThemeProvider>
    </LanguageProvider>
  );
}
