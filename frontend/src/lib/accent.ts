/**
 * WCAG relative luminance of a `#rrggbb`, which is what decides whether something is light or dark for the
 * purpose of putting one colour on another.
 */
export function relativeLuminance(hex: string): number {
  const value = hex.replace("#", "");
  const channel = (start: number): number =>
    parseInt(value.slice(start, start + 2), 16) / 255;
  const linear = (c: number): number =>
    c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  return (
    0.2126 * linear(channel(0)) +
    0.7152 * linear(channel(2)) +
    0.0722 * linear(channel(4))
  );
}

/**
 * A readable foreground (near-black or white) for text/icons placed on a solid accent color,
 * chosen by WCAG relative luminance. This is what keeps a free-form hex accent legible.
 */
export function accentForeground(hex: string): string {
  return relativeLuminance(hex) > 0.5 ? "#1a1426" : "#ffffff";
}
