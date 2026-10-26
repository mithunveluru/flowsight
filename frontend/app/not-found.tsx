import Link from "next/link";
import { Band } from "@/components/ui/band";
import { Button } from "@/components/ui/button";
import { Logo } from "@/components/layout/logo";
import "@/components/landing/landing.css";

export default function NotFound() {
  return (
    <Band tone="navy" space="none" bleed className="lp-hero flex min-h-screen flex-col">
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 sm:px-6 lg:px-12">
        <Link href="/" className="flex min-h-bar w-fit items-center gap-sp-lg text-ty-button text-on-primary">
          <Logo />
          FlowSight
        </Link>
        <main className="flex flex-1 flex-col justify-center pb-sp-page">
          <h1 className="text-ty-hero-title text-on-primary md:text-ty-display">
            <span className="lp-line">
              <span>This page is not here.</span>
            </span>
          </h1>
          <p className="lp-rise mt-sp-giant max-w-xl text-ty-body-lg text-on-primary/90">
            The link may be out of date, or the address mistyped.
          </p>
          <div className="lp-rise mt-sp-max flex flex-wrap gap-sp-wide" style={{ "--i": 1 } as React.CSSProperties}>
            <Button asChild size="lg" className="bg-on-primary text-primary-deep hover:bg-band-blue">
              <Link href="/">Back to home</Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-on-primary bg-transparent text-on-primary hover:bg-on-primary/10"
            >
              <Link href="/dashboard">Open dashboard</Link>
            </Button>
          </div>
        </main>
      </div>
    </Band>
  );
}
