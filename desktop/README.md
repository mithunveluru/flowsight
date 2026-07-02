# FlowSight Desktop Companion

A lightweight Tauri 2 desktop companion for FlowSight — Raycast-style quick
access to the platform's highest-value actions and insights. It is **not** a
separate product: it reuses the existing backend, API, DTOs, analytics, OCR
pipeline, AI insights, and the web app's design system. The backend remains the
single source of truth; no business logic is duplicated here.

## What it does

- **Overview** — financial flexibility score, top AI signals, spending snapshot
  (today / this week / tx count / top category), recent transactions.
- **Capture** — quick transaction entry and receipt OCR (drag-drop, file pick,
  clipboard paste → OCR → review → save).
- **Insights** — behavioral patterns, leak alerts, recommendations.
- **Desktop** — system tray, global shortcut (`Cmd/Ctrl+Shift+Space`), frameless
  popup, hide-on-blur, native notifications for new high-priority signals,
  auto-refresh on focus + background sync.

## Architecture

- `src/` — Vite + React 19 + Tailwind UI. Reuses the web app's design tokens,
  the AI Signals primitives (`@/components/ui/signals`), and motion primitives
  via the `@` alias that points at `../frontend`. DTO **types** are imported
  from `@/features/*/types`; thin API wrappers in `src/api/*` call the same
  backend endpoints the web app uses.
- `src/lib/api.ts` — token-aware fetch client (mirrors the web app's), JWT from
  the Tauri store rather than browser localStorage.
- `src-tauri/` — Rust shell: window, tray, global shortcut, hide-on-blur, and
  two app commands (`hide_window`, `open_workspace`).

The companion authenticates with the same `POST /api/v1/auth/login` and stores
its JWT via `tauri-plugin-store`.

## Prerequisites (one-time)

Node is already required for the web app. For the native shell you also need the
Rust toolchain and (on Linux) the WebKitGTK system libraries:

```bash
# Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Linux system deps (Debian/Ubuntu)
sudo apt update
sudo apt install -y libwebkit2gtk-4.1-dev build-essential curl wget file \
  libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev
```

macOS needs Xcode Command Line Tools (`xcode-select --install`); Windows needs
the Microsoft C++ Build Tools + WebView2 (preinstalled on Win 11).

## Run

```bash
cd desktop
npm install          # JS deps (already done if you cloned with node_modules)
npm run tauri:dev    # launches the native window with hot reload
```

The backend must be running (the web stack via `docker compose up`). The API
base defaults to `http://localhost:8080`; override with `VITE_API_URL`.

> The backend CORS allow-list already includes the Tauri origins
> (`tauri://localhost`, `http://tauri.localhost`) and the dev server
> (`http://localhost:1420`) — see `CORS_ALLOWED_ORIGINS`.

## Build installers

```bash
npm run tauri:build   # produces per-OS bundles in src-tauri/target/release/bundle
```

## Verify without the native toolchain

The UI and its cross-project reuse can be validated without Rust:

```bash
npm run type-check    # tsc --noEmit — passes
npm run build         # vite build — bundles the shared frontend components
```

## Notes

- Icons were generated from a brand source via `npx tauri icon`. Regenerate with
  `npx tauri icon <1024px.png>`.
- If `cargo` reports a minor API mismatch on first build (Tauri plugin version
  drift), it will be in `src-tauri/src/lib.rs` around the global-shortcut or tray
  setup — those are the only version-sensitive call sites.
