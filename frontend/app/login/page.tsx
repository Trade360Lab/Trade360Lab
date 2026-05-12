"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/features/auth/auth-provider";
import type { AuthSession } from "@/features/auth/auth-storage";

type AuthApiResponse = AuthSession;

async function readErrorMessage(response: Response) {
  try {
    const payload = (await response.json()) as { message?: string };
    if (typeof payload.message === "string" && payload.message.trim().length > 0) {
      return payload.message;
    }
  } catch {
    // Ignore malformed error payloads and use fallback below.
  }
  return `Request failed with status ${response.status}`;
}

export default function LoginPage() {
  const router = useRouter();
  const { isReady, isAuthenticated, login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isReady && isAuthenticated) {
      router.replace("/workspace");
    }
  }, [isAuthenticated, isReady, router]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response));
      }

      const session = (await response.json()) as AuthApiResponse;
      window.sessionStorage.setItem(
        "tradelab.auth-success",
        JSON.stringify({ email: session.user.email })
      );
      login(session);
      router.replace("/workspace");
    } catch (submissionError) {
      setError(
        submissionError instanceof Error ? submissionError.message : "Login failed"
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top,rgba(43,213,118,0.18),transparent_38%),linear-gradient(180deg,hsl(var(--tl-bg-1)),hsl(var(--tl-bg-0)))] px-4 py-10">
      <div className="w-full max-w-md rounded-[28px] border border-[hsl(var(--tl-border-1)/0.72)] bg-[linear-gradient(180deg,hsl(var(--tl-bg-1)/0.96),hsl(var(--tl-bg-0)/0.98))] p-8 shadow-[0_30px_90px_rgba(0,0,0,0.18)]">
        <div className="mb-8">
          <div className="text-xs uppercase tracking-[0.26em] text-muted-foreground">
            Trade360Lab
          </div>
          <h1 className="mt-3 text-3xl font-semibold text-foreground">Login</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Access to runs, datasets, and strategies now requires a user session.
          </p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label className="text-sm text-foreground" htmlFor="email">
              Email
            </label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <label className="text-sm text-foreground" htmlFor="password">
              Password
            </label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              required
            />
          </div>

          {error ? (
            <div className="rounded-[16px] border border-status-failed/30 bg-status-failed/10 px-4 py-3 text-sm text-status-failed">
              {error}
            </div>
          ) : null}

          <Button className="w-full" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Signing in..." : "Sign in"}
          </Button>
        </form>

        <p className="mt-6 text-sm text-muted-foreground">
          No account yet?{" "}
          <Link className="text-[#c9ef4e] hover:underline" href="/register">
            Register
          </Link>
        </p>
      </div>
    </div>
  );
}
