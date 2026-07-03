import { invoke } from "@tauri-apps/api/core";

// Thin wrappers over the Rust commands; views never import the Tauri API directly.

// hide the popup (Raycast-style dismiss)
export function hideWindow(): Promise<void> {
  return invoke("hide_window");
}

// open the web workspace in the default browser
export function openWorkspace(): Promise<void> {
  return invoke("open_workspace");
}
