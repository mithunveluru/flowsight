import type { Metadata } from "next";
import { Showcase } from "./showcase";

export const metadata: Metadata = {
  title: "UI kit",
  robots: { index: false, follow: false },
};

// Development-only reference for the design system; next.config.ts leaves this file out of production builds.
export default function UiKitPage() {
  return <Showcase />;
}
