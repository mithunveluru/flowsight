import { Card } from "@/components/ui/card";
import { AuthSide } from "@/components/landing/auth-side";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen w-full bg-canvas lg:grid lg:grid-cols-[1fr_1fr]">
      <AuthSide />
      <main className="flex items-start justify-center px-4 py-sp-max sm:px-6 sm:py-sp-hero lg:min-h-screen lg:items-center lg:px-12">
        <Card variant="outline" padding="xl" className="w-full max-w-md rounded-lg">
          {children}
        </Card>
      </main>
    </div>
  );
}
