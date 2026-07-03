import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

// Density derived from actual shell width (ResizeObserver), not a window-mode label.
export type Density = "compact" | "medium" | "large";

function fromWidth(w: number): Density {
  if (w < 440) return "compact";
  if (w < 720) return "medium";
  return "large";
}

const DensityCtx = createContext<Density>("compact");

export function useDensity(): Density {
  return useContext(DensityCtx);
}

export function DensityProvider({ children }: { children: ReactNode }) {
  const [density, setDensity] = useState<Density>(() =>
    fromWidth(typeof window !== "undefined" ? window.innerWidth : 400)
  );

  useEffect(() => {
    const el = document.getElementById("root") ?? document.body;
    const update = () => setDensity(fromWidth(el.clientWidth || window.innerWidth));
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener("resize", update);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", update);
    };
  }, []);

  return <DensityCtx.Provider value={density}>{children}</DensityCtx.Provider>;
}
