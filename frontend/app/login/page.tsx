"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { Github, Send } from "lucide-react";
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
    <main className="flex min-h-screen items-center justify-center bg-[#ecefed] px-4 py-8 text-[#141716] sm:px-6 lg:px-8">
      <section className="grid w-full max-w-[1120px] overflow-hidden rounded-[38px] bg-white p-2 shadow-[0_32px_90px_rgba(20,24,22,0.14)] lg:min-h-[680px] lg:grid-cols-[1.05fr_0.95fr]">
        <div className="relative min-h-[280px] overflow-hidden rounded-[32px] lg:min-h-full">
          <Image
            src="/auth/trader-login.jpg"
            alt=""
            fill
            sizes="(max-width: 1024px) 100vw, 560px"
            className="object-cover"
            priority
          />
        </div>

        <div className="flex min-h-[560px] items-center justify-center px-6 py-10 sm:px-10 lg:px-14">
          <div className="w-full max-w-[380px]">
            <div className="mb-12">
              <div className="text-[13px] font-semibold uppercase tracking-[0.24em] text-[#2a322f]">
                Trade360Lab
              </div>
              <h1 className="mt-16 text-center text-5xl font-semibold tracking-tight text-[#111413] sm:text-[56px]">
                Hi Trader
              </h1>
              <p className="mt-4 text-center text-sm font-medium text-[#68706c]">
                Welcome back
              </p>
            </div>

            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label className="sr-only" htmlFor="email">
                  Email
                </label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="Email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  className="h-12 rounded-[14px] border-[#d8ddda] bg-white px-4 text-[#151817] placeholder:text-[#8b938f] focus-visible:ring-[#2bd576]/45"
                  required
                />
              </div>
              <div className="space-y-2">
                <label className="sr-only" htmlFor="password">
                  Password
                </label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="Password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="h-12 rounded-[14px] border-[#d8ddda] bg-white px-4 text-[#151817] placeholder:text-[#8b938f] focus-visible:ring-[#2bd576]/45"
                  minLength={8}
                  required
                />
              </div>

              <div className="flex justify-end">
                <Link
                  href="#"
                  className="text-xs font-medium text-[#65706b] transition-colors hover:text-[#1d2420] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2bd576]/45"
                >
                  Forgot password?
                </Link>
              </div>

              {error ? (
                <div className="rounded-[16px] border border-status-failed/30 bg-status-failed/10 px-4 py-3 text-sm text-status-failed">
                  {error}
                </div>
              ) : null}

              <div className="flex items-center gap-4 py-1 text-xs text-[#8c9490]">
                <div className="h-px flex-1 bg-[#dfe4e1]" />
                <span>or continue with</span>
                <div className="h-px flex-1 bg-[#dfe4e1]" />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <Button
                  type="button"
                  variant="outline"
                  className="h-12 rounded-[14px] border-[#d8ddda] bg-white text-[#202522] shadow-none hover:bg-[#f5f7f6] hover:text-[#111413]"
                >
                  <Github className="h-4 w-4" />
                  GitHub
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="h-12 rounded-[14px] border-[#d8ddda] bg-white text-[#202522] shadow-none hover:bg-[#f5f7f6] hover:text-[#111413]"
                >
                  <Send className="h-4 w-4" />
                  Telegram
                </Button>
              </div>

              <Button
                className="h-12 w-full rounded-full border-0 bg-[linear-gradient(135deg,#2bd576,#6ff7a3)] text-[#07110b] shadow-[0_16px_34px_rgba(43,213,118,0.24)] hover:brightness-105"
                type="submit"
                disabled={isSubmitting}
              >
                {isSubmitting ? "Signing in..." : "Login"}
              </Button>
            </form>

            <p className="mt-7 text-center text-sm text-[#68706c]">
              Don&apos;t have an account?{" "}
              <Link
                className="font-semibold text-[#1f8f57] transition-colors hover:text-[#14683f] hover:underline"
                href="/register"
              >
                Sign up
              </Link>
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
