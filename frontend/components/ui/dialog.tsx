"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cva, type VariantProps } from "class-variance-authority";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

// Radix supplies focus trap, Esc to close, aria-modal and title/description wiring.
const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogClose = DialogPrimitive.Close;
const DialogPortal = DialogPrimitive.Portal;

const DialogOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      "fixed inset-0 z-50 bg-overlay/50 dark:bg-overlay/70",
      "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:duration-base data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:duration-fast",
      "motion-reduce:animate-none",
      className
    )}
    {...props}
  />
));
DialogOverlay.displayName = DialogPrimitive.Overlay.displayName;

function CloseButton() {
  return (
    <DialogPrimitive.Close
      className="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground transition-colors duration-fast hover:bg-band hover:text-ink"
    >
      <X className="h-4 w-4" strokeWidth={1.75} aria-hidden="true" />
      <span className="sr-only">Close</span>
    </DialogPrimitive.Close>
  );
}

/** Radix only restores focus to a Trigger; drawers opened from a plain button hand it back here. */
function useReturnFocus(onCloseAutoFocus?: (event: Event) => void) {
  const [opener] = React.useState(() => (typeof document === "undefined" ? null : document.activeElement));
  return (event: Event) => {
    onCloseAutoFocus?.(event);
    if (event.defaultPrevented) return;
    event.preventDefault();
    if (opener instanceof HTMLElement && opener !== document.body && opener.isConnected) opener.focus();
  };
}

interface ContentExtras {
  hideClose?: boolean;
}

const DialogContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & ContentExtras
>(({ className, children, hideClose, onCloseAutoFocus, ...props }, ref) => {
  const returnFocus = useReturnFocus(onCloseAutoFocus);
  return (
  <DialogPortal>
    <DialogOverlay />
    <DialogPrimitive.Content
      ref={ref}
      onCloseAutoFocus={returnFocus}
      className={cn(
        "fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2",
        "max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-lg border border-border bg-popover p-6 text-popover-foreground sm:p-sp-max",
        "fx-pop",
        className
      )}
      {...props}
    >
      {children}
      {!hideClose && <CloseButton />}
    </DialogPrimitive.Content>
  </DialogPortal>
  );
});
DialogContent.displayName = DialogPrimitive.Content.displayName;

const sheetVariants = cva(
  cn(
    "fixed z-50 flex flex-col bg-popover text-popover-foreground",
    "data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:duration-slow data-[state=closed]:duration-base",
    "motion-reduce:animate-none"
  ),
  {
    variants: {
      side: {
        right:
          "inset-y-0 right-0 h-full w-full border-l border-border sm:max-w-md data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right",
        left:
          "inset-y-0 left-0 h-full w-full border-r border-border sm:max-w-md data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left",
        bottom:
          "inset-x-0 bottom-0 max-h-[85dvh] rounded-t-lg border-t border-border data-[state=open]:slide-in-from-bottom data-[state=closed]:slide-out-to-bottom",
      },
    },
    defaultVariants: { side: "right" },
  }
);

const Sheet = DialogPrimitive.Root;
const SheetTrigger = DialogPrimitive.Trigger;
const SheetClose = DialogPrimitive.Close;

/** Side panel built on Dialog: modal, focus-trapped, closes on Esc and overlay click. */
const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> &
    VariantProps<typeof sheetVariants> &
    ContentExtras
>(({ side, className, children, hideClose, onCloseAutoFocus, ...props }, ref) => {
  const returnFocus = useReturnFocus(onCloseAutoFocus);
  return (
    <DialogPortal>
      <DialogOverlay />
      <DialogPrimitive.Content ref={ref} onCloseAutoFocus={returnFocus} className={cn(sheetVariants({ side }), className)} {...props}>
        {children}
        {!hideClose && <CloseButton />}
      </DialogPrimitive.Content>
    </DialogPortal>
  );
});
SheetContent.displayName = "SheetContent";

function DialogHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("space-y-1.5 pr-8", className)} {...props} />;
}

function DialogFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end", className)}
      {...props}
    />
  );
}

const DialogTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title ref={ref} className={cn("text-headline", className)} {...props} />
));
DialogTitle.displayName = DialogPrimitive.Title.displayName;

const DialogDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description ref={ref} className={cn("text-supporting", className)} {...props} />
));
DialogDescription.displayName = DialogPrimitive.Description.displayName;

export {
  Dialog,
  DialogTrigger,
  DialogClose,
  DialogPortal,
  DialogOverlay,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
  Sheet,
  SheetTrigger,
  SheetClose,
  SheetContent,
  DialogHeader as SheetHeader,
  DialogFooter as SheetFooter,
  DialogTitle as SheetTitle,
  DialogDescription as SheetDescription,
};
