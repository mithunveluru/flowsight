"use client";

import * as React from "react";

export type Politeness = "polite" | "assertive";
type Announce = (message: string, politeness?: Politeness) => void;

const AnnouncerContext = React.createContext<Announce>(() => {});

// Screen readers skip a live region whose text did not change, so clear first.
const REANNOUNCE_DELAY_MS = 60;

/** Mounts one polite and one assertive live region for the whole app; use `useAnnounce()` to speak. */
export function AnnouncerProvider({ children }: { children: React.ReactNode }) {
  const [polite, setPolite] = React.useState("");
  const [assertive, setAssertive] = React.useState("");
  const timers = React.useRef<Record<Politeness, ReturnType<typeof setTimeout> | undefined>>({
    polite: undefined,
    assertive: undefined,
  });

  const announce = React.useCallback<Announce>((message, politeness = "polite") => {
    const set = politeness === "assertive" ? setAssertive : setPolite;
    clearTimeout(timers.current[politeness]);
    set("");
    timers.current[politeness] = setTimeout(() => set(message), REANNOUNCE_DELAY_MS);
  }, []);

  React.useEffect(() => {
    const pending = timers.current;
    return () => {
      clearTimeout(pending.polite);
      clearTimeout(pending.assertive);
    };
  }, []);

  return (
    <AnnouncerContext.Provider value={announce}>
      {children}
      <div className="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {polite}
      </div>
      <div className="sr-only" role="alert" aria-live="assertive" aria-atomic="true">
        {assertive}
      </div>
    </AnnouncerContext.Provider>
  );
}

export function useAnnounce(): Announce {
  return React.useContext(AnnouncerContext);
}

/** Local live region for status text that belongs next to its control (e.g. "3 rows selected"). */
export function LiveRegion({
  children,
  politeness = "polite",
  visible = false,
  className,
}: {
  children: React.ReactNode;
  politeness?: Politeness;
  visible?: boolean;
  className?: string;
}) {
  return (
    <div
      aria-live={politeness}
      aria-atomic="true"
      role={politeness === "assertive" ? "alert" : "status"}
      className={visible ? className : "sr-only"}
    >
      {children}
    </div>
  );
}
