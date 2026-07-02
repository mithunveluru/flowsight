// FlowSight desktop companion — Tauri 2 shell.
//
// Responsibilities live entirely on the Rust side so the webview stays a thin,
// fast UI: a frameless window with two modes, a system tray, a global show/hide
// shortcut, and Raycast-style hide-on-blur (Widget mode only). All business
// logic stays in the backend; this process only manages the window.
//
// Two window modes:
//   * Widget   — compact, always-on-top, off-taskbar launcher. Hides on blur.
//   * Expanded — a normal resizable desktop app window (taskbar, maximize,
//                snap, drag-resize). Does NOT hide on blur.

use tauri::{
    menu::{Menu, MenuItem, PredefinedMenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, LogicalSize, Manager,
};
use tauri_runtime::ResizeDirection;

const WORKSPACE_URL: &str = "http://localhost:3007/dashboard";

/// Bring the popup to the foreground, centered on the active monitor.
fn show_and_focus(app: &AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        let _ = win.center();
        let _ = win.show();
        let _ = win.set_focus();
    }
}

/// Toggle popup visibility (global shortcut + tray left-click).
fn toggle_window(app: &AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        match win.is_visible() {
            Ok(true) => {
                let _ = win.hide();
            }
            _ => show_and_focus(app),
        }
    }
}

#[tauri::command]
fn hide_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        let _ = win.hide();
    }
}

#[tauri::command]
fn open_workspace(app: AppHandle) {
    use tauri_plugin_opener::OpenerExt;
    if let Err(e) = app.opener().open_url(WORKSPACE_URL, None::<&str>) {
        eprintln!("failed to open workspace: {e}");
    }
}

/// Switch between the compact Widget launcher and the resizable Expanded app.
#[tauri::command]
fn set_window_mode(app: AppHandle, mode: String) {
    let Some(win) = app.get_webview_window("main") else { return };
    let expanded = mode == "expanded";

    if expanded {
        let _ = win.set_always_on_top(false);
        let _ = win.set_skip_taskbar(false);
        let _ = win.set_resizable(true);
        let _ = win.set_size(LogicalSize::new(820.0, 840.0));
        let _ = win.center();
    } else {
        let _ = win.unmaximize();
        let _ = win.set_always_on_top(true);
        let _ = win.set_skip_taskbar(true);
        let _ = win.set_resizable(true);
        let _ = win.set_size(LogicalSize::new(400.0, 600.0));
        let _ = win.center();
    }
    let _ = win.set_focus();
}

#[tauri::command]
fn minimize_window(app: AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        let _ = win.minimize();
    }
}

#[tauri::command]
fn toggle_maximize(app: AppHandle) {
    if let Some(win) = app.get_webview_window("main") {
        if win.is_maximized().unwrap_or(false) {
            let _ = win.unmaximize();
        } else {
            let _ = win.maximize();
        }
    }
}

/// Begin an OS drag-resize from a window edge/corner (frameless windows need
/// this since there is no native border to grab).
#[tauri::command]
fn start_resize(app: AppHandle, direction: String) {
    // start_resize_dragging lives on Window (not WebviewWindow) in this version.
    let Some(win) = app.get_window("main") else { return };
    let dir = match direction.as_str() {
        "north" => ResizeDirection::North,
        "south" => ResizeDirection::South,
        "east" => ResizeDirection::East,
        "west" => ResizeDirection::West,
        "north-east" => ResizeDirection::NorthEast,
        "north-west" => ResizeDirection::NorthWest,
        "south-east" => ResizeDirection::SouthEast,
        "south-west" => ResizeDirection::SouthWest,
        _ => return,
    };
    let _ = win.start_resize_dragging(dir);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        // single-instance MUST be registered first: a second launch (desktop icon
        // or the GNOME-registered hotkey) is routed to the running instance, which
        // simply shows + focuses the window instead of opening a duplicate. This is
        // how the companion is summoned on Wayland, where in-app global shortcuts
        // are not delivered.
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            show_and_focus(app);
        }))
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .invoke_handler(tauri::generate_handler![
            hide_window,
            open_workspace,
            set_window_mode,
            minimize_window,
            toggle_maximize,
            start_resize
        ])
        .setup(|app| {
            // ---- Global show/hide shortcut --------------------------------
            #[cfg(desktop)]
            {
                use tauri_plugin_global_shortcut::{GlobalShortcutExt, ShortcutState};
                let handle = app.handle().clone();
                if let Err(e) = app.global_shortcut().on_shortcut(
                    "CommandOrControl+Shift+Space",
                    move |_app, _shortcut, event| {
                        if event.state == ShortcutState::Pressed {
                            toggle_window(&handle);
                        }
                    },
                ) {
                    eprintln!("failed to register global shortcut: {e}");
                }
            }

            // ---- System tray ----------------------------------------------
            let open_i = MenuItem::with_id(app, "open", "Open FlowSight", true, None::<&str>)?;
            let capture_i =
                MenuItem::with_id(app, "capture", "Quick Capture", true, None::<&str>)?;
            let workspace_i =
                MenuItem::with_id(app, "workspace", "Open Full Workspace", true, None::<&str>)?;
            let quit_i = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let sep = PredefinedMenuItem::separator(app)?;
            let menu = Menu::with_items(
                app,
                &[&open_i, &capture_i, &workspace_i, &sep, &quit_i],
            )?;

            let icon = app
                .default_window_icon()
                .cloned()
                .expect("a default window icon must be configured");

            TrayIconBuilder::with_id("main-tray")
                .icon(icon)
                .tooltip("FlowSight")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_and_focus(app),
                    "capture" => {
                        show_and_focus(app);
                        let _ = app.emit("navigate", "capture");
                    }
                    "workspace" => open_workspace(app.clone()),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        toggle_window(tray.app_handle());
                    }
                })
                .build(app)?;

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running FlowSight desktop");
}
