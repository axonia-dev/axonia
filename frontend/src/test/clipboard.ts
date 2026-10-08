/**
 * Puts a stub on `navigator.clipboard` for the current test. user-event's `setup()` installs a
 * getter-only stub of its own, so a plain assignment throws where this defines the property.
 */
export function stubClipboard(clipboard: Partial<Clipboard>) {
  Object.defineProperty(navigator, "clipboard", {
    value: clipboard,
    configurable: true,
  });
}
