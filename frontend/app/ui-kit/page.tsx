import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Showcase } from "./showcase";

export const metadata: Metadata = {
  title: "UI kit",
  robots: { index: false, follow: false },
};

// Development-only reference for the design system; production builds serve a 404.
export default function UiKitPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <Showcase />;
}
