"use client";

import * as React from "react";
import { TabUnderline, useTabUnderline } from "@/components/motion/tab-underline";
import { cn } from "@/lib/utils";

/** Shared look of a tab label; the tab rail uses the same classes. Inactive tabs grow to 44px on touch. */
export function tabClass(active: boolean) {
  return cn(
    "relative inline-flex shrink-0 items-center whitespace-nowrap px-sp-xl transition-colors duration-fast ease-standard",
    active
      ? "h-11 text-ty-link font-medium text-primary md:text-ty-subhead"
      : "h-8 text-ty-caption text-ink-nav hover:text-primary coarse:h-11"
  );
}

export const tabIds = (prefix: string, value: string) => ({
  tab: `${prefix}-tab-${value}`,
  panel: `${prefix}-panel-${value}`,
});

export interface TabItem<T extends string> {
  value: T;
  label: React.ReactNode;
}

/** Content tabs with a spring underline. Pair each with a <TabPanel> using the same prefix. */
export function Tabs<T extends string>({
  items,
  value,
  onValueChange,
  label,
  idPrefix,
  className,
}: {
  items: TabItem<T>[];
  value: T;
  onValueChange: (value: T) => void;
  label: string;
  idPrefix: string;
  className?: string;
}) {
  const { ref, geometry } = useTabUnderline<HTMLDivElement>(value);
  const buttons = React.useRef<Record<string, HTMLButtonElement | null>>({});

  const onKeyDown = (e: React.KeyboardEvent) => {
    const index = items.findIndex((i) => i.value === value);
    const last = items.length - 1;
    const next =
      e.key === "ArrowRight" ? (index + 1) % items.length
      : e.key === "ArrowLeft" ? (index - 1 + items.length) % items.length
      : e.key === "Home" ? 0
      : e.key === "End" ? last
      : -1;
    if (next < 0) return;
    e.preventDefault();
    const target = items[next].value;
    onValueChange(target);
    buttons.current[target]?.focus();
  };

  return (
    <div className={cn("overflow-x-auto", className)}>
      <div ref={ref} role="tablist" aria-label={label} onKeyDown={onKeyDown} className="relative flex items-end">
        {items.map((item) => {
          const active = item.value === value;
          const ids = tabIds(idPrefix, item.value);
          return (
            <button
              key={item.value}
              ref={(el) => {
                buttons.current[item.value] = el;
              }}
              id={ids.tab}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls={ids.panel}
              tabIndex={active ? 0 : -1}
              data-active={active}
              onClick={() => onValueChange(item.value)}
              className={tabClass(active)}
            >
              {item.label}
            </button>
          );
        })}
        {geometry && <TabUnderline x={geometry.x} width={geometry.width} />}
      </div>
    </div>
  );
}

export function TabPanel({
  idPrefix,
  value,
  active,
  className,
  children,
}: {
  idPrefix: string;
  value: string;
  active: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  const ids = tabIds(idPrefix, value);
  return (
    <div id={ids.panel} role="tabpanel" aria-labelledby={ids.tab} hidden={!active} tabIndex={0} className={className}>
      {active ? children : null}
    </div>
  );
}
