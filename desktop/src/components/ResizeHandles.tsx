import { startResize } from "~/lib/window-mode";

// Invisible edge/corner grips for the frameless Expanded window; forward drags to the OS.
const HANDLES: [string, string][] = [
  ["north", "top-0 left-2 right-2 h-1 cursor-n-resize"],
  ["south", "bottom-0 left-2 right-2 h-1 cursor-s-resize"],
  ["west", "left-0 top-2 bottom-2 w-1 cursor-w-resize"],
  ["east", "right-0 top-2 bottom-2 w-1 cursor-e-resize"],
  ["north-west", "top-0 left-0 h-2.5 w-2.5 cursor-nw-resize"],
  ["north-east", "top-0 right-0 h-2.5 w-2.5 cursor-ne-resize"],
  ["south-west", "bottom-0 left-0 h-2.5 w-2.5 cursor-sw-resize"],
  ["south-east", "bottom-0 right-0 h-2.5 w-2.5 cursor-se-resize"],
];

export function ResizeHandles() {
  return (
    <>
      {HANDLES.map(([dir, cls]) => (
        <div
          key={dir}
          className={`no-drag absolute z-50 ${cls}`}
          onMouseDown={(e) => {
            e.preventDefault();
            void startResize(dir);
          }}
        />
      ))}
    </>
  );
}
