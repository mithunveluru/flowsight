import { FlatCompat } from "@eslint/eslintrc";

// eslint-config-next 15 still ships eslintrc-style configs only, so wrap it.
const compat = new FlatCompat({ baseDirectory: import.meta.dirname });

const config = [
  { ignores: [".next/**", "out/**", "build/**", "next-env.d.ts"] },
  ...compat.extends("next/core-web-vitals"),
];

export default config;
