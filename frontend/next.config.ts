import type { NextConfig } from "next";

// Baseline security headers for every route. A full script CSP is omitted:
// Next.js needs nonce plumbing for it, and the app renders no user-supplied
// HTML (React auto-escapes). frame-ancestors 'none' still blocks clickjacking.
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  },
];

// Routes renamed to match their navigation labels. Temporary (307) for one
// release so the old names can still be reused if needed, then permanent.
export const renamedRoutes: [from: string, to: string][] = [
  ["/dashboard/transactions/new", "/dashboard/capture/manual"],
  ["/dashboard/receipts/upload", "/dashboard/capture/receipt"],
  ["/dashboard/analytics", "/dashboard/trends"],
  ["/dashboard/insights", "/dashboard/habits"],
  ["/dashboard/leaks", "/dashboard/drains"],
  ["/dashboard/recurring", "/dashboard/subscriptions"],
  ["/dashboard/simulate", "/dashboard/what-if"],
];

const nextConfig: NextConfig = {
  output: "standalone",
  // the ui-kit gallery is app/ui-kit/page.dev.tsx, so it only exists outside production builds
  pageExtensions: process.env.NODE_ENV === "production" ? ["tsx", "ts"] : ["tsx", "ts", "dev.tsx"],
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  async redirects() {
    return renamedRoutes.map(([source, destination]) => ({ source, destination, permanent: false }));
  },
};

export default nextConfig;
