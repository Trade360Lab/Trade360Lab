import { proxyToBackend } from "@/lib/server/backend-proxy";

export async function GET() {
  return proxyToBackend({
    path: "/api/python/readiness",
    errorMessage: "Python parser readiness check failed",
  });
}
