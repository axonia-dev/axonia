import { normalizeTag } from "@/lib/normalize-tag";

interface AreaTint {
  background: string;
  foreground: string;
  border: string;
}

export interface AreaHue {
  light: AreaTint;
  dark: AreaTint;
}

function hue(light: AreaTint, dark: AreaTint): AreaHue {
  return { light, dark };
}

export const AREA_HUES: readonly AreaHue[] = [
  hue(
    { background: "#ffe4e6", foreground: "#9f1239", border: "#e11d48" },
    { background: "#4c0519", foreground: "#fecdd3", border: "#fb7185" },
  ),
  hue(
    { background: "#fef3c7", foreground: "#92400e", border: "#b45309" },
    { background: "#451a03", foreground: "#fde68a", border: "#fbbf24" },
  ),
  hue(
    { background: "#d1fae5", foreground: "#065f46", border: "#047857" },
    { background: "#022c22", foreground: "#a7f3d0", border: "#34d399" },
  ),
  hue(
    { background: "#ccfbf1", foreground: "#115e59", border: "#0f766e" },
    { background: "#042f2e", foreground: "#99f6e4", border: "#2dd4bf" },
  ),
  hue(
    { background: "#e0f2fe", foreground: "#075985", border: "#0369a1" },
    { background: "#082f49", foreground: "#bae6fd", border: "#38bdf8" },
  ),
  hue(
    { background: "#e0e7ff", foreground: "#3730a3", border: "#4f46e5" },
    { background: "#1e1b4b", foreground: "#c7d2fe", border: "#818cf8" },
  ),
  hue(
    { background: "#fae8ff", foreground: "#86198f", border: "#c026d3" },
    { background: "#4a044e", foreground: "#f5d0fe", border: "#e879f9" },
  ),
  hue(
    { background: "#ffedd5", foreground: "#9a3412", border: "#c2410c" },
    { background: "#431407", foreground: "#fed7aa", border: "#fb923c" },
  ),
];

const FNV_OFFSET = 0x811c9dc5;
const FNV_PRIME = 0x01000193;

function fnv1a(text: string): number {
  let hash = FNV_OFFSET;
  for (let at = 0; at < text.length; at++) {
    hash ^= text.charCodeAt(at);
    hash = Math.imul(hash, FNV_PRIME) >>> 0;
  }
  return hash;
}

/** One area is one colour everywhere: the tint is picked by a hash of its normalized slug. */
export function areaHue(slug: string): AreaHue {
  return AREA_HUES[fnv1a(normalizeTag(slug)) % AREA_HUES.length]!;
}
