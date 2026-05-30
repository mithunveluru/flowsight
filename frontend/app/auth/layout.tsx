import { Showcase } from "./_components/showcase";

/**
 * Auth shell.
 *
 * Desktop: two-column grid. Left holds the product narrative (Showcase).
 * Right holds the form, on clean white. The visual balance favours the left.
 *
 * Mobile: a compact narrative strip sits above the form. The form remains
 * primary; storytelling is present but does not crowd the keyboard surface.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen w-full bg-white lg:grid lg:grid-cols-[1.2fr_1fr]">
      {/* Desktop: showcase on the left */}
      <aside className="relative hidden lg:block">
        <Showcase />
      </aside>

      {/* Right column wraps the mobile showcase + form so they stack on mobile */}
      <main className="flex min-h-screen flex-col">
        {/* Mobile-only compact showcase */}
        <div className="lg:hidden">
          <Showcase variant="mobile" />
        </div>

        {/* Form area */}
        <div className="flex flex-1 items-start justify-center px-6 py-10 sm:py-14 lg:items-center lg:px-12 lg:py-16 xl:px-16">
          <div className="w-full max-w-sm">{children}</div>
        </div>
      </main>
    </div>
  );
}
