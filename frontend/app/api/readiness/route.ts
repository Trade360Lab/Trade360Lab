import { proxyToBackend } from "@/lib/server/backend-proxy";

export async function GET() {
  return proxyToBackend({
    path: "/api/readiness",
    errorMessage: "Java API readiness check failed",
  });
}
