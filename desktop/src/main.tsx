import React from "react";
import ReactDOM from "react-dom/client";
import App from "~/App";
import { MotionRoot } from "@/components/motion/primitives";
import { followSystemTheme } from "@/components/ui/theme-core";
import "~/index.css";

followSystemTheme();

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <MotionRoot>
      <App />
    </MotionRoot>
  </React.StrictMode>
);
