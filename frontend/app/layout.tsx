import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import { GeistMono } from "geist/font/mono";
import { Providers } from "@/components/providers";
import { THEME_INIT_SCRIPT } from "@/components/ui/theme-core";
import "./globals.css";

// Variable weight axis (no weight option) so 350 renders as 350, not 300 or 400.
const inter = Inter({ subsets: ["latin", "latin-ext"], variable: "--font-sans", display: "swap" });

export const metadata: Metadata = {
  title: {
    default: "FlowSight",
    template: "%s - FlowSight",
  },
  description:
    "See where your money goes, find spending worth recovering, and understand the habits behind it, all in one place.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="en"
      className={`${inter.variable} ${GeistMono.variable}`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="min-h-screen bg-background font-sans antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
