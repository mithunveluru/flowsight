import {
  Activity,
  LayoutGrid,
  PlusCircle,
  Sparkles,
  ExternalLink,
  X,
  Minus,
  Square,
  Maximize2,
  Minimize2,
} from "lucide-react";
import { cn } from "~/lib/utils";
import { hideWindow, openWorkspace } from "~/lib/tauri";
import { useWindowMode, minimizeWindow, toggleMaximize } from "~/lib/window-mode";
import { useDensity } from "~/lib/density";

export type ViewKey = "overview" | "capture" | "insights";

const TABS: { key: ViewKey; label: string; Icon: typeof LayoutGrid }[] = [
  { key: "overview", label: "Overview", Icon: LayoutGrid },
  { key: "capture", label: "Add", Icon: PlusCircle },
  { key: "insights", label: "Insights", Icon: Sparkles },
];

function WinButton({
  title,
  onClick,
  children,
}: {
  title: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      className="flex h-6 w-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
    >
      {children}
    </button>
  );
}

export function TitleBar({
  view,
  onChange,
}: {
  view: ViewKey;
  onChange: (v: ViewKey) => void;
}) {
  const density = useDensity();
  const { mode, toggle } = useWindowMode();
  const expanded = mode === "expanded";
  const showLabels = density !== "compact";

  return (
    <header className="shrink-0 border-b border-border/70 bg-background/40 backdrop-blur-md">
      <div
        data-tauri-drag-region
        className="drag-region flex items-center justify-between px-3.5 pt-3 pb-2"
      >
        <div data-tauri-drag-region className="flex items-center gap-2">
          <span
            data-tauri-drag-region
            className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-primary-foreground"
          >
            <Activity className="h-3.5 w-3.5" strokeWidth={2.5} />
          </span>
          <span
            data-tauri-drag-region
            className="text-[13px] font-semibold tracking-tight text-foreground"
          >
            FlowSight
          </span>
        </div>
        <div className="no-drag flex items-center gap-0.5">
          <WinButton title="Open full workspace" onClick={() => void openWorkspace()}>
            <ExternalLink className="h-3.5 w-3.5" strokeWidth={2} />
          </WinButton>
          <WinButton
            title={expanded ? "Collapse to widget" : "Expand window"}
            onClick={toggle}
          >
            {expanded ? (
              <Minimize2 className="h-3.5 w-3.5" strokeWidth={2} />
            ) : (
              <Maximize2 className="h-3.5 w-3.5" strokeWidth={2} />
            )}
          </WinButton>
          {expanded && (
            <>
              <WinButton title="Minimize" onClick={() => void minimizeWindow()}>
                <Minus className="h-3.5 w-3.5" strokeWidth={2} />
              </WinButton>
              <WinButton title="Maximize" onClick={() => void toggleMaximize()}>
                <Square className="h-3 w-3" strokeWidth={2} />
              </WinButton>
            </>
          )}
          <WinButton title={expanded ? "Close" : "Close (Esc)"} onClick={() => void hideWindow()}>
            <X className="h-3.5 w-3.5" strokeWidth={2} />
          </WinButton>
        </div>
      </div>

      <nav className="flex items-center gap-1 px-3 pb-2.5">
        {TABS.map(({ key, label, Icon }) => {
          const active = view === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => onChange(key)}
              title={label}
              className={cn(
                "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors",
                active
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:bg-muted/60 hover:text-foreground"
              )}
            >
              <Icon className="h-3.5 w-3.5" strokeWidth={2} />
              {showLabels && label}
            </button>
          );
        })}
      </nav>
    </header>
  );
}
