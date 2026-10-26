import type { Config } from "tailwindcss";
import { sharedTheme } from "../frontend/tailwind.theme";

// Theme comes from the web app so the reused ui/* and signals primitives render
// identically. Content globs include the shared frontend components so the
// classes used there are generated into the desktop bundle.
const { extend, borderRadius } = sharedTheme;

const config: Config = {
  darkMode: ["class"],
  content: [
    "./index.html",
    "./src/**/*.{ts,tsx}",
    "../frontend/components/**/*.{ts,tsx}",
    "../frontend/features/**/*.{ts,tsx}",
  ],
  theme: { borderRadius, extend },
  plugins: [require("tailwindcss-animate")],
};

export default config;
