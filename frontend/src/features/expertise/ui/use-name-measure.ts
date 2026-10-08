import { useLayoutEffect, useState } from "react";

import {
  type MeasureText,
  type NameStyle,
  styleAttributes,
} from "@/features/expertise/ui/name-style";

const SVG_NS = "http://www.w3.org/2000/svg";

function styleKey(style: NameStyle): string {
  return Object.values(styleAttributes(style)).join("|");
}

function probeFor(svg: SVGSVGElement, style: NameStyle): SVGTextElement {
  const text = document.createElementNS(SVG_NS, "text");
  for (const [name, value] of Object.entries(styleAttributes(style)))
    text.setAttribute(name, value);
  svg.append(text);
  return text;
}

function measurer(svg: SVGSVGElement): MeasureText {
  const probes = new Map<string, SVGTextElement>();
  const widths = new Map<string, number>();
  return (text, style) => {
    const key = styleKey(style);
    const known = widths.get(`${key}:${text}`);
    if (known !== undefined) return known;
    const probe = probes.get(key) ?? probeFor(svg, style);
    probes.set(key, probe);
    probe.textContent = text;
    const width = probe.getComputedTextLength();
    widths.set(`${key}:${text}`, width);
    return width;
  };
}

/**
 * Measures text in the very font, size and tracking the shape draws it in. It starts measuring before the first paint
 * when the page's fonts are already in, and starts afresh whenever another face finishes loading; wherever nothing
 * lays text out (jsdom) it returns undefined and the layout estimates.
 */
export function useNameMeasure(): MeasureText | undefined {
  const [measure, setMeasure] = useState<MeasureText>();
  useLayoutEffect(() => {
    if (!("getComputedTextLength" in document.createElementNS(SVG_NS, "text")))
      return;
    const svg = document.createElementNS(SVG_NS, "svg");
    svg.setAttribute("aria-hidden", "true");
    svg.style.cssText =
      "position:absolute;width:0;height:0;overflow:hidden;visibility:hidden";
    document.body.append(svg);
    const rebuild = () => {
      svg.replaceChildren();
      setMeasure(() => measurer(svg));
    };
    if (document.fonts.status === "loaded") rebuild();
    document.fonts.addEventListener("loadingdone", rebuild);
    return () => {
      document.fonts.removeEventListener("loadingdone", rebuild);
      svg.remove();
    };
  }, []);
  return measure;
}
