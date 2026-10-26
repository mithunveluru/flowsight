// The widget has no theme switch: it follows the OS, like the tray and the window chrome do.
export function followSystemTheme() {
  const query = window.matchMedia("(prefers-color-scheme: dark)");
  const apply = () => {
    document.documentElement.classList.toggle("dark", query.matches);
    document.documentElement.style.colorScheme = query.matches ? "dark" : "light";
  };
  apply();
  query.addEventListener("change", apply);
}
