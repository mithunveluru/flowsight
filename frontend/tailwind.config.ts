import type { Config } from "tailwindcss";
import { sharedTheme } from "./tailwind.theme";

const { extend, borderRadius } = sharedTheme;

const config: Config = {
  darkMode: ["class"],
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./features/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    container: {
      center: true,
      padding: "1.5rem",
      screens: {
        "2xl": "1400px",
      },
    },
    borderRadius,
    extend,
  },
  plugins: [require("tailwindcss-animate")],
};

export default config;
