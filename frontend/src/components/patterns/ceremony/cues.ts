import { type Beat, easeOutBack, easeOutCubic, progress } from "./timeline";

export type CueStyle = Readonly<Record<string, string>>;

export interface Cue {
  readonly selector: string;
  readonly beat: Beat;
  readonly style: (p: number) => CueStyle;
}

export function part(name: string): string {
  return `[data-ceremony-part="${name}"]`;
}

/** Every cue styles its elements at `t`, including before its beat (the starting state). */
export function applyCues(
  root: ParentNode,
  cues: readonly Cue[],
  t: number,
): void {
  for (const cue of cues) {
    const style = cue.style(progress(cue.beat, t));
    for (const element of root.querySelectorAll<HTMLElement>(cue.selector)) {
      for (const [property, value] of Object.entries(style)) {
        element.style.setProperty(property, value);
      }
    }
  }
}

export function fadeIn(p: number): CueStyle {
  return { opacity: String(p) };
}

export function rise(distance: number): (p: number) => CueStyle {
  return (p) => ({
    opacity: String(p),
    transform: `translateY(${((1 - easeOutCubic(p)) * distance).toFixed(2)}px)`,
  });
}

export function popIn(p: number): CueStyle {
  return {
    opacity: String(Math.min(1, p * 2)),
    transform: `scale(${easeOutBack(p).toFixed(3)})`,
  };
}

const NO_SHEEN: CueStyle = {
  "background-image": "",
  "background-clip": "",
  "-webkit-background-clip": "",
  color: "",
};

/** One specular glint sweeping across a part's text, left to right. */
export function sheen(p: number): CueStyle {
  if (p <= 0 || p >= 1) {
    return NO_SHEEN;
  }
  const at = -20 + 140 * easeOutCubic(p);
  return {
    "background-image": `linear-gradient(100deg, var(--foreground) ${at - 12}%, #ffffff ${at}%, var(--foreground) ${at + 12}%)`,
    "background-clip": "text",
    "-webkit-background-clip": "text",
    color: "transparent",
  };
}
