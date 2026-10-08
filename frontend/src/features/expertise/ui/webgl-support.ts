/**
 * There is no software fallback renderer. A browser without WebGL2, which in practice means a locked-down enterprise
 * desktop, gets the readings panel alone, and the panel is complete on its own: the lists carry every fact the map
 * shows. That is the same outcome as below the md breakpoint, not a degradation.
 *
 * Asked once per document and remembered, because the answer cannot change and asking is not free: every probe
 * takes a real WebGL context, and a browser keeps only about sixteen of them alive before it starts losing the
 * oldest. Called from a render body - which is where a support test belongs - an unremembered probe spends the
 * whole budget within a couple of seconds of dragging, and the context the map is drawn with is the one evicted:
 * the picture goes black under the reader's hand.
 */
let answer: boolean | null = null;

export function hasWebGl2(): boolean {
  answer ??= probe();
  return answer;
}

/** The probe hands its context straight back rather than waiting for the collector, so it never counts against the
 *  contexts the drawing itself needs. */
function probe(): boolean {
  try {
    const gl = document.createElement("canvas").getContext("webgl2");
    if (gl === null) return false;
    gl.getExtension("WEBGL_lose_context")?.loseContext();
    return true;
  } catch {
    return false;
  }
}

/**
 * What a renderer's pixel ratio is set to. Capped at two: past it a retina screen buys nothing a reader can see and
 * costs four times the fill. Both drawings ask for it, and anything sized in device pixels has to divide by the
 * same number they multiplied by.
 */
export function rendererPixelRatio(): number {
  return Math.min(2, globalThis.devicePixelRatio || 1);
}
