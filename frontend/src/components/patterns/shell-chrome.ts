import type { CSSProperties } from "react";

export type ShellChromePart = "rail" | "header" | "topbar" | "tabs";

export interface ShellChrome {
  "data-shell-chrome": ShellChromePart;
  style: CSSProperties;
}

export function shellChrome(part: ShellChromePart, shell: string): ShellChrome {
  return {
    "data-shell-chrome": part,
    style: { "--shell-chrome": `shell-${part}-${shell}` } as CSSProperties,
  };
}
