/** Scroll offset that centres a tab in the rail's viewport, clamped to the scrollable range. */
export function centerScrollLeft(tabLeft: number, tabWidth: number, viewport: number, content: number): number {
  const target = tabLeft - (viewport - tabWidth) / 2;
  return Math.max(0, Math.min(target, Math.max(0, content - viewport)));
}
