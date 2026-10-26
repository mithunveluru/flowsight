import Link from "next/link";
import { Receipt, Repeat, Sliders } from "lucide-react";
import { Logo } from "@/components/layout/logo";
import "./landing.css";

const points = [
  { icon: Repeat, text: "Find recurring charges and price rises" },
  { icon: Receipt, text: "Turn a receipt photo into a draft you review" },
  { icon: Sliders, text: "Test a big purchase before you make it" },
];

/** Navy gradient panel beside the auth card; collapses to a short strip on narrow screens. */
export function AuthSide() {
  return (
    <aside className="hero-navy lp-hero flex flex-col justify-between gap-sp-hero px-4 py-sp-giant sm:px-6 lg:min-h-screen lg:px-12 lg:py-sp-band">
      <Link href="/" className="flex min-h-11 w-fit items-center gap-sp-lg text-ty-button text-on-primary">
        <Logo />
        FlowSight
      </Link>
      <div className="hidden lg:block">
        <p className="max-w-md text-ty-hero-title text-on-primary">Understand the decisions behind your finances.</p>
        <ul className="mt-sp-max space-y-sp-wide">
          {points.map(({ icon: Icon, text }, i) => (
            <li
              key={text}
              className="lp-rise flex items-center gap-sp-xl text-ty-body text-on-primary/90"
              style={{ "--i": i } as React.CSSProperties}
            >
              <Icon className="h-5 w-5 shrink-0 text-sky" strokeWidth={1.75} aria-hidden="true" />
              {text}
            </li>
          ))}
        </ul>
      </div>
      <p className="hidden text-ty-caption text-on-primary/80 lg:block">Private by design. We never connect to your bank.</p>
    </aside>
  );
}
