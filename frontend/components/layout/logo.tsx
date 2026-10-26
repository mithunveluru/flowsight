export function Logo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 22 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect width="22" height="22" rx="4" fill="hsl(var(--primary))" />
      <path
        d="M5.5 14.75L9 10.75L12 13.25L16.5 7.75"
        stroke="hsl(var(--primary-foreground))"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="16.5" cy="7.75" r="1.25" fill="hsl(var(--primary-foreground))" />
    </svg>
  );
}
