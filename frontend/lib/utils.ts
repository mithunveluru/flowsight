import { type ClassValue, clsx } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// text-ty-* are font-size roles; without this twMerge reads them as colours and drops text-<colour>
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [
        {
          text: [
            "ty-display", "ty-hero-title", "ty-section-title", "ty-lead", "ty-subhead", "ty-body-lg",
            "ty-body", "ty-button", "ty-link", "ty-nav-link", "ty-list-item", "ty-caption", "2xs",
          ],
        },
      ],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
