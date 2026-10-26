"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import { useReducedMotion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import { TabUnderline, useTabUnderline } from "@/components/motion/tab-underline";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/components/ui/menu";
import { tabClass } from "@/components/ui/tabs";
import { RECENT_RECEIPTS_SIZE, useRecentReceipts } from "@/features/receipts/hooks";
import { countDrafts } from "@/features/today/logic";
import { RECEIPT_INBOX_HREF, activeRailKey, moreItems, railItems } from "./nav";
import { centerScrollLeft } from "./rail-core";

/** Product tab rail: the primary sections as tabs, everything else under More. Scrolls sideways when narrow. */
export function TabRail() {
  const pathname = usePathname();
  const active = activeRailKey(pathname);
  const reduced = useReducedMotion();
  const scroller = useRef<HTMLDivElement>(null);
  const { ref, geometry } = useTabUnderline<HTMLDivElement>(active);
  const receipts = useRecentReceipts();
  const drafts = receipts.data ? countDrafts(receipts.data.content) : 0;

  useEffect(() => {
    const view = scroller.current;
    const el = view?.querySelector<HTMLElement>('[data-active="true"]');
    if (!view || !el) return;
    view.scrollTo({
      left: centerScrollLeft(el.offsetLeft, el.offsetWidth, view.clientWidth, view.scrollWidth),
      behavior: reduced ? "auto" : "smooth",
    });
  }, [active, reduced]);

  return (
    <nav aria-label="Main" className="shrink-0 border-b border-border bg-canvas">
      <div ref={scroller} className="overflow-x-auto px-3 [scrollbar-width:none] sm:px-6 lg:px-8 [&::-webkit-scrollbar]:hidden">
        <div ref={ref} className="relative flex w-max min-w-full items-end">
          {railItems.map((item) => {
            const current = active === item.href;
            const badge = item.href === RECEIPT_INBOX_HREF && drafts > 0 ? drafts : null;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={current ? "page" : undefined}
                data-active={current}
                className={tabClass(current)}
              >
                {item.tab}
                {badge != null && (
                  <span className="ml-1.5 rounded-sm bg-band-blue px-1.5 text-xs font-medium tabular-nums text-primary">
                    {badge >= RECENT_RECEIPTS_SIZE ? `${RECENT_RECEIPTS_SIZE}+` : badge}
                    <span className="sr-only"> {badge === 1 ? "draft" : "drafts"} awaiting review</span>
                  </span>
                )}
              </Link>
            );
          })}
          <Menu>
            <MenuTrigger asChild>
              <button type="button" data-active={active === "more"} className={tabClass(active === "more")}>
                More
                <ChevronDown className="ml-1 h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
              </button>
            </MenuTrigger>
            <MenuContent align="start">
              {moreItems.map((item) => (
                <MenuItem key={item.href} asChild>
                  <Link href={item.href}>
                    <item.icon className="h-4 w-4 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
                    {item.label}
                  </Link>
                </MenuItem>
              ))}
            </MenuContent>
          </Menu>
          {geometry && <TabUnderline x={geometry.x} width={geometry.width} />}
        </div>
      </div>
    </nav>
  );
}
