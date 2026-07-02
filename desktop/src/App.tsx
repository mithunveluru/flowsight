import { useEffect, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { listen } from "@tauri-apps/api/event";
import { AnimatedSwitch } from "@/components/motion/primitives";
import { useAuth } from "~/lib/auth-store";
import { bumpRefresh } from "~/lib/refresh";
import { hideWindow } from "~/lib/tauri";
import { startBackgroundSync } from "~/lib/sync";
import { useWindowMode } from "~/lib/window-mode";
import { DensityProvider } from "~/lib/density";
import { TitleBar, type ViewKey } from "~/components/TitleBar";
import { Toaster } from "~/components/Toaster";
import { ResizeHandles } from "~/components/ResizeHandles";
import { Spinner } from "~/components/ui";
import { LoginView } from "~/views/LoginView";
import { OverviewView } from "~/views/OverviewView";
import { CaptureView, type CaptureTab } from "~/views/CaptureView";
import { InsightsView } from "~/views/InsightsView";

export default function App() {
  const { token, ready, hydrate } = useAuth();
  const mode = useWindowMode((s) => s.mode);
  const [view, setView] = useState<ViewKey>("overview");
  const [captureTab, setCaptureTab] = useState<CaptureTab>("transaction");

  // Jump to the Capture view with a specific tab (quick actions / tray).
  function goCapture(tab: CaptureTab) {
    setCaptureTab(tab);
    setView("capture");
  }

  // Load the persisted session once on launch.
  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  // Re-fetch whenever the popup regains focus (Raycast-style "fresh on show").
  useEffect(() => {
    const win = getCurrentWindow();
    const unlisten = win.onFocusChanged(({ payload: focused }) => {
      if (focused) bumpRefresh();
    });
    return () => {
      void unlisten.then((f) => f());
    };
  }, []);

  // Tray "Quick Capture" jumps straight to the capture view.
  useEffect(() => {
    const unlisten = listen<string>("navigate", (e) => setView(e.payload as ViewKey));
    return () => {
      void unlisten.then((f) => f());
    };
  }, []);

  // Esc dismisses the popup, like a launcher.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") void hideWindow();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Background sync (auto-refresh + notifications) runs only while signed in.
  useEffect(() => {
    if (!token) return;
    const stop = startBackgroundSync();
    return stop;
  }, [token]);

  let body: React.ReactNode;
  if (!ready) {
    body = (
      <div className="app-shell relative items-center justify-center">
        <Spinner className="h-5 w-5" />
      </div>
    );
  } else if (!token) {
    body = (
      <div className="app-shell relative">
        <LoginView />
        <Toaster />
      </div>
    );
  } else {
    body = (
      <div className="app-shell relative">
        {mode === "expanded" && <ResizeHandles />}
        <TitleBar view={view} onChange={setView} />
        <main className="relative flex-1 overflow-y-auto">
          <AnimatedSwitch viewKey={view} className="h-full">
            {view === "overview" && <OverviewView onNavigate={setView} onCapture={goCapture} />}
            {view === "capture" && <CaptureView initialTab={captureTab} />}
            {view === "insights" && <InsightsView />}
          </AnimatedSwitch>
        </main>
        <Toaster />
      </div>
    );
  }

  return <DensityProvider>{body}</DensityProvider>;
}
