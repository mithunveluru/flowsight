"use client";

import Link from "next/link";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { ChevronDown, Plus } from "lucide-react";
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
    "inline-flex h-9 items-center gap-1.5 bg-primary text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90";

  return (
    <div className={cn("inline-flex shrink-0 items-stretch rounded-lg shadow-sm", className)}>
      {!compact && (
        <Link href={primary.href} className={cn(segment, "rounded-l-lg px-3")}>
          <primary.icon className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
          {primary.label}
        </Link>
      )}
      <DropdownMenu.Root>
        <DropdownMenu.Trigger
          aria-label={compact ? "Capture" : "More ways to capture"}
          className={cn(
            segment,
            compact ? "rounded-lg px-2.5" : "rounded-r-lg border-l border-primary-foreground/20 px-2"
          )}
        >
          {compact ? (
            <Plus className="h-4 w-4" strokeWidth={2} aria-hidden="true" />
          ) : (
            <ChevronDown className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
          )}
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <DropdownMenu.Content
            align="end"
            sideOffset={6}
            className="z-50 min-w-[220px] rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-elev-3"
          >
            <DropdownMenu.Label className="px-2.5 pb-1 pt-1.5 text-overline">Capture</DropdownMenu.Label>
            {captureActions.map((action) => (
              <DropdownMenu.Item key={action.href} asChild>
                <Link
                  href={action.href}
                  className="flex cursor-default items-start gap-2.5 rounded px-2.5 py-2 text-sm outline-none data-[highlighted]:bg-muted"
                >
                  <action.icon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block font-medium text-foreground">{action.label}</span>
                    {action.hint && <span className="block text-xs text-muted-foreground">{action.hint}</span>}
                  </span>
                </Link>
              </DropdownMenu.Item>
            ))}
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    </div>
  );
}
