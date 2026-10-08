import type { AxisKind } from "@/features/expertise/domain/practitioner-shape";

/** How the shape sets a piece of text; `advance` is the average glyph width in em, measured in Chromium. */
export interface NameStyle {
  className: string;
  fontSize: number;
  fontWeight: number;
  /** Letter spacing, in em. */
  tracking: number;
  advance: number;
}

/** A text's rendered width in a style: measured in a browser, estimated elsewhere. */
export type MeasureText = (text: string, style: NameStyle) => number;

/** Below this drawing width the names step down one size, so a phone keeps a readable drawing. */
const COMPACT_BELOW = 360;

/** The map's own faces: tracked display capitals for a neighbourhood, display as typed for a named area, sans for an area. */
export function nameStyle(kind: AxisKind, width: number): NameStyle {
  const compact = width < COMPACT_BELOW;
  switch (kind) {
    case "neighbourhood":
      return {
        className: "font-display",
        fontSize: compact ? 10 : 11,
        fontWeight: 600,
        tracking: compact ? 0.06 : 0.1,
        advance: 0.63,
      };
    case "named":
      return {
        className: "font-display",
        fontSize: compact ? 11 : 12,
        fontWeight: 600,
        tracking: 0,
        advance: 0.52,
      };
    case "area":
      return {
        className: "font-sans",
        fontSize: compact ? 11 : 12,
        fontWeight: 600,
        tracking: 0,
        advance: 0.53,
      };
  }
}

export function ringNameStyle(fontSize: number): NameStyle {
  return {
    className: "font-sans",
    fontSize,
    fontWeight: 400,
    tracking: 0,
    advance: 0.49,
  };
}

export function estimateText(text: string, style: NameStyle): number {
  return text.length * style.fontSize * (style.advance + style.tracking);
}

/** The props that draw a text in a style. */
export function textProps(style: NameStyle) {
  return {
    className: style.className,
    fontSize: style.fontSize,
    fontWeight: style.fontWeight,
    ...(style.tracking > 0 ? { letterSpacing: `${style.tracking}em` } : {}),
  };
}

/** The same, as attributes on a measuring probe. */
export function styleAttributes(style: NameStyle): Record<string, string> {
  return {
    class: style.className,
    "font-size": String(style.fontSize),
    "font-weight": String(style.fontWeight),
    ...(style.tracking > 0 ? { "letter-spacing": `${style.tracking}em` } : {}),
  };
}
