import Link from "next/link";
import type { ReactNode } from "react";
import { Band } from "@/components/ui/band";
import { Logo } from "@/components/layout/logo";
import { TextLink } from "@/components/ui/text-link";
import { LEGAL } from "./legal-config";

export type LegalSection = { id: string; title: string; body: ReactNode };

export function LegalPage({
  title,
  intro,
  sections,
}: {
  title: string;
  intro: ReactNode;
  sections: LegalSection[];
}) {
  return (
    <div className="min-h-screen scroll-smooth bg-canvas">
      <Band as="header" tone="chrome" space="none">
        <div className="flex min-h-bar items-center justify-between">
          <Link href="/" className="flex min-h-11 items-center gap-sp-lg text-ty-button text-ink">
            <Logo />
            {LEGAL.product}
          </Link>
          <TextLink href="/" className="flex min-h-11 items-center">
            Back to home
          </TextLink>
        </div>
      </Band>

      <Band as="div" tone="blue" space="md">
        <p className="text-ty-caption text-ink-meta">Legal</p>
        <h1 className="mt-sp-sm text-ty-hero-title text-ink md:text-ty-section-title">{title}</h1>
        <p className="mt-sp-sm text-ty-caption text-ink-meta">Last updated {LEGAL.updated}</p>
      </Band>

      <main>
        <Band as="div" tone="canvas" space="xl">
          <div className="lg:grid lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-sp-page">
            <nav aria-label="Table of contents" className="hidden lg:block">
              <div className="sticky top-sp-page">
                <p className="mb-sp-xl text-ty-caption text-ink">On this page</p>
                <ul className="space-y-sp-xs border-l border-border">
                  {sections.map((s) => (
                    <li key={s.id}>
                      <a
                        href={`#${s.id}`}
                        className="-ml-px block border-l-2 border-transparent py-sp-sm pl-sp-wide text-sm text-ink-meta hover:border-primary hover:text-ink"
                      >
                        {s.title}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            </nav>

            <article className="min-w-0 max-w-2xl">
              <div className="text-ty-body text-ink-list">{intro}</div>

              <div className="mt-sp-hero space-y-sp-hero">
                {sections.map((s) => (
                  <section key={s.id} id={s.id} className="scroll-mt-sp-page">
                    <h2 className="text-ty-subhead text-ink">{s.title}</h2>
                    <div className="prose-legal mt-sp-xl space-y-sp-xl text-ty-link text-ink-list [&_a]:text-primary [&_a]:underline [&_li]:pl-1 [&_strong]:font-medium [&_strong]:text-ink [&_ul]:list-disc [&_ul]:space-y-sp-sm [&_ul]:pl-5">
                      {s.body}
                    </div>
                  </section>
                ))}
              </div>
            </article>
          </div>
        </Band>
      </main>

      <Band as="footer" tone="band" space="md">
        <div className="flex flex-wrap items-center gap-x-sp-giant gap-y-sp-sm text-ty-caption">
          <TextLink href="/privacy">Privacy Policy</TextLink>
          <TextLink href="/terms">Terms of Service</TextLink>
          <span className="text-ink-meta">© 2026 {LEGAL.product}</span>
        </div>
      </Band>
    </div>
  );
}
