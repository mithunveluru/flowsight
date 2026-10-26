"use client";

import * as React from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

const Menu = DropdownMenu.Root;
const MenuTrigger = DropdownMenu.Trigger;
const MenuRadioGroup = DropdownMenu.RadioGroup;

const MenuContent = React.forwardRef<
  React.ElementRef<typeof DropdownMenu.Content>,
  React.ComponentPropsWithoutRef<typeof DropdownMenu.Content>
>(({ className, sideOffset = 6, align = "end", ...props }, ref) => (
  <DropdownMenu.Portal>
    <DropdownMenu.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn(
        "z-50 min-w-56 rounded-md border border-border bg-popover p-1 text-popover-foreground",
        "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-[.98] data-[state=open]:duration-base",
        "data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-[.98] data-[state=closed]:duration-fast",
        "motion-reduce:animate-none",
        className
      )}
      {...props}
    />
  </DropdownMenu.Portal>
));
MenuContent.displayName = "MenuContent";

const itemClass =
  "flex min-h-10 cursor-default select-none items-center gap-2.5 rounded-sm px-2.5 py-2 text-sm text-ink-body outline-none data-[highlighted]:bg-band-blue data-[highlighted]:text-ink data-[disabled]:cursor-not-allowed data-[disabled]:opacity-40";

const MenuItem = React.forwardRef<
  React.ElementRef<typeof DropdownMenu.Item>,
  React.ComponentPropsWithoutRef<typeof DropdownMenu.Item>
>(({ className, ...props }, ref) => <DropdownMenu.Item ref={ref} className={cn(itemClass, className)} {...props} />);
MenuItem.displayName = "MenuItem";

const MenuRadioItem = React.forwardRef<
  React.ElementRef<typeof DropdownMenu.RadioItem>,
  React.ComponentPropsWithoutRef<typeof DropdownMenu.RadioItem>
>(({ className, children, ...props }, ref) => (
  <DropdownMenu.RadioItem ref={ref} className={cn(itemClass, "relative pr-8", className)} {...props}>
    {children}
    <DropdownMenu.ItemIndicator className="absolute right-2.5">
      <Check className="h-4 w-4 text-primary" strokeWidth={2} aria-hidden="true" />
    </DropdownMenu.ItemIndicator>
  </DropdownMenu.RadioItem>
));
MenuRadioItem.displayName = "MenuRadioItem";

const MenuLabel = React.forwardRef<
  React.ElementRef<typeof DropdownMenu.Label>,
  React.ComponentPropsWithoutRef<typeof DropdownMenu.Label>
>(({ className, ...props }, ref) => (
  <DropdownMenu.Label ref={ref} className={cn("px-2.5 pb-1 pt-1.5 text-xs font-medium text-muted-foreground", className)} {...props} />
));
MenuLabel.displayName = "MenuLabel";

const MenuSeparator = React.forwardRef<
  React.ElementRef<typeof DropdownMenu.Separator>,
  React.ComponentPropsWithoutRef<typeof DropdownMenu.Separator>
>(({ className, ...props }, ref) => (
  <DropdownMenu.Separator ref={ref} className={cn("-mx-1 my-1 h-px bg-border", className)} {...props} />
));
MenuSeparator.displayName = "MenuSeparator";

export { Menu, MenuTrigger, MenuContent, MenuItem, MenuLabel, MenuSeparator, MenuRadioGroup, MenuRadioItem };
