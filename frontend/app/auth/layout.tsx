import { Showcase } from "./_components/showcase";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen w-full bg-white lg:grid lg:grid-cols-[1.2fr_1fr]">
      <aside className="relative hidden lg:block">
        <Showcase />
      </aside>

      <main className="flex min-h-screen flex-col">
        <div className="lg:hidden">
          <Showcase variant="mobile" />
        </div>

        <div className="flex flex-1 items-start justify-center px-6 py-10 sm:py-14 lg:items-center lg:px-12 lg:py-16 xl:px-16">
          <div className="w-full max-w-sm">{children}</div>
        </div>
      </main>
    </div>
  );
}
