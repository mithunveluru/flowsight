"use client";

import { useEffect, useRef } from "react";
import { ArrowUpRight, Receipt, Repeat } from "lucide-react";
import { cn } from "@/lib/utils";

// Connector paths live in the 560 x 440 box the stage is laid out on.
const CONNECTORS = [
  "M 214 128 C 262 128, 268 176, 318 190",
  "M 340 268 C 340 318, 300 322, 262 338",
];

function Bar({ className }: { className?: string }) {
  return <span className={cn("block h-2 rounded-sm bg-band", className)} />;
}

function Card({
  index,
  depth,
  from,
  className,
  children,
}: {
  index: number;
  depth: number;
  from: [number, number];
  className: string;
  children: React.ReactNode;
}) {
  return (
    <div className={cn("lp-par absolute", className)} style={{ "--depth": depth } as React.CSSProperties}>
      <div
        className="lp-card card-illustration p-sp-wide text-sm text-ink-body"
        style={{ "--i": index, "--fx": `${from[0]}px`, "--fy": `${from[1]}px` } as React.CSSProperties}
      >
        {children}
      </div>
    </div>
  );
}

/** Composed product illustration. Decorative: the same facts are in the copy beside it. */
export function HeroArt({ className }: { className?: string }) {
  const stage = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = stage.current;
    if (!el || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let frame = 0;
    const update = () => {
      frame = 0;
      el.style.setProperty("--p", String(Math.min(window.scrollY / 600, 1)));
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div
      ref={stage}
      aria-hidden="true"
      className={cn("lp-stage relative mx-auto w-full max-w-[560px]", className)}
      style={{ aspectRatio: "560 / 440" }}
    >
      <div
        className="lp-par absolute inset-x-[6%] inset-y-[10%]"
        style={{ "--depth": 10 } as React.CSSProperties}
      >
        <div className="lp-frame h-full w-full rounded-lg bg-on-primary/15" />
      </div>

      <svg viewBox="0 0 560 440" className="absolute inset-0 h-full w-full" fill="none">
        <defs>
          {CONNECTORS.map((_, i) => (
            <mask key={i} id={`lp-conn-${i}`} maskUnits="userSpaceOnUse" x="0" y="0" width="560" height="440">
              <path
                d={CONNECTORS[i]}
                pathLength={1}
                stroke="white"
                strokeWidth="6"
                className="lp-draw"
                style={{ "--i": i } as React.CSSProperties}
              />
            </mask>
          ))}
        </defs>
        {CONNECTORS.map((d, i) => (
          <path
            key={i}
            d={d}
            mask={`url(#lp-conn-${i})`}
            className="lp-dots stroke-on-primary"
            strokeWidth="3.5"
            strokeLinecap="round"
            strokeDasharray="0.1 10"
          />
        ))}
      </svg>

      <Card index={0} depth={34} from={[-24, 12]} className="left-0 top-[4%] w-[40%]">
        <div className="flex items-center gap-2 text-ink">
          <Receipt className="h-4 w-4 text-primary" strokeWidth={1.75} />
          <span className="font-medium">Receipt</span>
        </div>
        <div className="mt-sp-xl space-y-sp-sm">
          <Bar className="w-2/3" />
          <Bar className="w-full" />
          <Bar className="w-5/6" />
        </div>
        <p className="mt-sp-xl rounded-sm bg-band-blue px-2 py-1 text-xs text-primary-outline">Review before saving</p>
      </Card>

      <Card index={1} depth={-14} from={[24, 12]} className="right-0 top-[30%] w-[44%]">
        <div className="flex items-center gap-2 text-ink">
          <Repeat className="h-4 w-4 text-primary" strokeWidth={1.75} />
          <span className="font-medium">Recurring payment</span>
        </div>
        <div className="mt-sp-xl space-y-sp-sm">
          <Bar className="w-1/2" />
          <Bar className="w-3/4" />
        </div>
        <p className="mt-sp-xl inline-flex items-center gap-1 rounded-sm bg-caution-soft px-2 py-1 text-xs text-caution">
          <ArrowUpRight className="h-3 w-3" strokeWidth={2} />
          Price went up
        </p>
      </Card>

      <Card index={2} depth={46} from={[-16, 24]} className="bottom-0 left-[8%] w-[46%]">
        <div className="font-medium text-ink">Flexibility score</div>
        <svg viewBox="0 0 100 56" className="mx-auto mt-sp-xl block w-3/4" fill="none">
          <path d="M 10 50 A 40 40 0 0 1 90 50" pathLength={1} strokeWidth="9" className="stroke-band" strokeLinecap="butt" />
          <path
            d="M 10 50 A 40 40 0 0 1 90 50"
            pathLength={1}
            strokeWidth="9"
            className="lp-gauge stroke-primary"
          />
        </svg>
        <p className="mt-sp-sm text-center text-xs text-ink-meta">Room left after fixed costs</p>
      </Card>
    </div>
  );
}
