"use client";

import Link from "next/link";
import { ChevronDown, Plus } from "lucide-react";
import { Menu, MenuContent, MenuItem, MenuLabel, MenuTrigger } from "@/components/ui/menu";
import { cn } from "@/lib/utils";
import { captureActions } from "./nav";

const [primary] = captureActions;

/**
 * Split button: the left half goes straight to the receipt scanner, the
 * chevron opens every capture path. `compact` collapses it to one "+" menu
 * button for narrow headers.
 */
export function CaptureMenu({ compact, className }: { compact?: boolean; className?: string }) {
  const segment =
    "press inline-flex h-9 items-center gap-1.5 bg-primary text-sm font-medium text-primary-foreground hover:bg-primary-hover active:bg-primary-pressed";

  return (
    <div className={cn("inline-flex shrink-0 items-stretch", className)}>
      {!compact && (
        <Link href={primary.href} className={cn(segment, "rounded-l-md px-3")}>
          <primary.icon className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
          {primary.label}
        </Link>
      )}
      <Menu>
        <MenuTrigger
          aria-label={compact ? "Capture" : "More ways to capture"}
          className={cn(
            segment,
            compact ? "rounded-md px-2.5" : "rounded-r-md border-l border-primary-foreground/30 px-2"
          )}
        >
          {compact ? (
            <Plus className="h-4 w-4" strokeWidth={2} aria-hidden="true" />
          ) : (
            <ChevronDown className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
          )}
        </MenuTrigger>
        <MenuContent>
          <MenuLabel>Capture</MenuLabel>
          {captureActions.map((action) => (
            <MenuItem key={action.href} asChild>
              <Link href={action.href} className="items-start">
                <action.icon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block font-medium text-ink">{action.label}</span>
                  {action.hint && <span className="block text-xs text-muted-foreground">{action.hint}</span>}
                </span>
              </Link>
            </MenuItem>
          ))}
        </MenuContent>
      </Menu>
    </div>
  );
}
