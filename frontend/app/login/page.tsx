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

const socialLinks = {
  telegram: "https://t.me/trading360l",
  github: "https://github.com/AlexToday111/TradeLab.git",
};

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
    <main className="flex min-h-screen items-center justify-center bg-[#ecefed] px-4 py-6 text-[#141716] sm:px-6 sm:py-8 lg:px-8">
      <section className="grid w-full max-w-[1240px] overflow-hidden rounded-[30px] bg-white p-2 shadow-[0_34px_96px_rgba(20,24,22,0.16)] sm:rounded-[42px] lg:min-h-[760px] lg:grid-cols-[1.12fr_0.88fr]">
        <div className="relative min-h-[270px] overflow-hidden rounded-[24px] sm:min-h-[360px] sm:rounded-[34px] lg:min-h-full">
          <Image
            src="/auth/Intro.png"
            alt=""
            fill
            sizes="(max-width: 1024px) 100vw, 560px"
            className="object-cover"
            priority
          />
        </div>

        <div className="flex min-h-[500px] justify-center px-6 py-9 sm:px-10 sm:py-10 lg:min-h-[560px] lg:px-14">
          <div className="flex w-full max-w-[380px] flex-col">
            <div className="text-[13px] font-semibold uppercase tracking-[0.24em] text-[#2a322f]">
              Trade360Lab
            </div>

            <div className="flex flex-1 flex-col justify-center">
              <div className="mb-10 sm:mb-12">
                <h1 className="mt-10 text-center text-4xl font-semibold tracking-tight text-[#111413] sm:mt-14 sm:text-[52px] lg:mt-16 lg:text-[56px]">
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

              <div className="mt-9 flex items-center justify-center gap-4">
                <a
                  href={socialLinks.github}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-[#d8ddda] bg-white text-[#202522] shadow-[0_10px_28px_rgba(20,24,22,0.08)] transition hover:-translate-y-0.5 hover:bg-[#f5f7f6] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2bd576]/45"
                  aria-label="GitHub"
                >
                  <Github className="h-4 w-4" />
                </a>
                <a
                  href={socialLinks.telegram}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-[#d8ddda] bg-white text-[#2aabee] shadow-[0_10px_28px_rgba(20,24,22,0.08)] transition hover:-translate-y-0.5 hover:bg-[#f5faff] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2aabee]/35"
                  aria-label="Telegram"
                >
                  <Send className="h-4 w-4" />
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
