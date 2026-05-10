import { expect, test, type Page } from "@playwright/test";

async function seedSession(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "tradelab.auth",
      JSON.stringify({
        token: "smoke-token",
        user: {
          id: 1,
          email: "smoke@example.test",
          createdAt: "2026-05-04T00:00:00Z",
        },
      })
    );
  });
  await page.route("**/api/health", (route) =>
    route.fulfill({ json: { status: "ok", service: "java-api" } })
  );
  await page.route("**/api/python/health", (route) =>
    route.fulfill({ json: { status: "ok", service: "python-parser" } })
  );
  await page.route("**/api/readiness", (route) =>
    route.fulfill({
      json: {
        status: "ready",
        service: "java-api",
        apiVersion: "0.9.2-alpha.1",
        database: "ok",
        migrationStatus: "flyway-baseline-present",
        liveTradingMode: "guarded-alpha",
        realOrderSubmissionEnabled: false,
        safety: "real order submission disabled by default",
      },
    })
  );
  await page.route("**/api/python/readiness", (route) =>
    route.fulfill({
      json: {
        status: "ready",
        service: "python-parser",
        parserVersion: "0.9.2-alpha.1",
        database: "ok",
        engineVersion: "python-execution-engine/0.9.2-alpha.1",
        internalAuthConfigured: "configured",
      },
    })
  );
  await page.route("**/api/live/risk/status", (route) =>
    route.fulfill({ json: { killSwitchActive: false, circuitBreakers: [] } })
  );
  await page.route("**/api/live/risk/events", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/live/certification/testnet/latest", (route) =>
    route.fulfill({ status: 404, json: { message: "not found" } })
  );
  await page.route(/\/api\/live\/exchange\/health.*/, (route) =>
    route.fulfill({
      json: {
        exchange: "binance",
        connected: true,
        credentialsValid: false,
        realOrderSubmissionEnabled: false,
        message: "smoke",
      },
    })
  );
  await page.route("**/api/live/credentials/status", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/live/sessions", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/live/orders", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/live/positions", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/strategies", (route) => route.fulfill({ json: [] }));
  await page.route("**/api/strategy-templates", (route) => route.fulfill({ json: [] }));
}

async function gotoReady(page: Page, path: string) {
  await page.goto(path, { waitUntil: "domcontentloaded" });
  await expect(page.locator("body")).toBeVisible();
}

test("auth boundary exposes login and register", async ({ page }) => {
  await gotoReady(page, "/login");
  await expect(page.getByRole("button", { name: /sign in|login|войти/i })).toBeVisible();

  await gotoReady(page, "/register");
  await expect(page.getByRole("button", { name: /create account|register|создать|зарегистр/i })).toBeVisible();
});

test("workspace and datasets pages render demo boundaries", async ({ page }) => {
  await seedSession(page);
  await gotoReady(page, "/workspace");
  await expect(page.getByTestId("demo-mode-badge").first()).toBeVisible();

  await gotoReady(page, "/data");
  await expect(page.getByTestId("demo-mode-badge").first()).toBeVisible();
});

test("strategy upload and run visibility surfaces render", async ({ page }) => {
  await seedSession(page);
  await gotoReady(page, "/strategies");
  await expect(page.getByText(/strategy|стратег/i).first()).toBeVisible();

  await gotoReady(page, "/backtests");
  await expect(page.getByText(/backtest|бэктест/i).first()).toBeVisible();
});

test("live trading safety and service health pages render", async ({ page }) => {
  await seedSession(page);
  await gotoReady(page, "/live");
  await expect(page.getByText("Safety state")).toBeVisible();
  await expect(page.getByText("Binance testnet certification")).toBeVisible();

  await gotoReady(page, "/settings");
  await expect(page.getByText("Readiness Dashboard")).toBeVisible();
  await expect(page.getByText("v0.9.2-alpha.1").first()).toBeVisible();
});
