import * as React from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

export interface TextLinkProps extends React.AnchorHTMLAttributes<HTMLAnchorElement> {
  href: string;
  // trailing chevron, the spec's marker for a link that goes somewhere
  chevron?: boolean;
  // set inside running text: underlined at rest
  inline?: boolean;
}

/** Link: primary colour, underline on hover (always, when `inline`). Internal paths use next/link. */
export function TextLink({ href, chevron, inline, className, children, ...props }: TextLinkProps) {
  const classes = cn("link inline-flex items-center gap-0.5 text-ty-link", inline && "link-inline", className);
  const content = (
    <>
      {children}
      {chevron && <ChevronRight className="h-4 w-4 shrink-0" strokeWidth={1.75} aria-hidden="true" />}
    </>
  );
  return href.startsWith("/") ? (
    <Link href={href} className={classes} {...props}>
      {content}
    </Link>
  ) : (
    <a href={href} className={classes} {...props}>
      {content}
    </a>
  );
}
