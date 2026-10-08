interface TextMeasuring {
  /** How many texts the stub has measured so far. */
  calls: () => number;
  /** How many `loadingdone` listeners are attached to `document.fonts`. */
  listeners: () => number;
  /** Lets another face finish loading, as `document.fonts` reports it. */
  loadFonts: () => void;
  restore: () => void;
}

/**
 * Gives jsdom what a browser has and it lacks: SVG text measuring at `perChar` pixels per character, and a
 * `document.fonts` that reports `status` and fires `loadingdone` when the test says so.
 */
export function stubTextMeasuring(
  perChar: number,
  status: FontFaceSetLoadStatus = "loading",
): TextMeasuring {
  let calls = 0;
  const fonts = new EventTarget() as EventTarget & {
    status: FontFaceSetLoadStatus;
  };
  fonts.status = status;
  let attached = 0;
  const add = fonts.addEventListener.bind(fonts);
  const remove = fonts.removeEventListener.bind(fonts);
  fonts.addEventListener = (...args: Parameters<typeof add>) => {
    attached += 1;
    add(...args);
  };
  fonts.removeEventListener = (...args: Parameters<typeof remove>) => {
    attached -= 1;
    remove(...args);
  };
  Object.defineProperty(SVGElement.prototype, "getComputedTextLength", {
    configurable: true,
    value(this: SVGElement) {
      calls += 1;
      return (this.textContent ?? "").length * perChar;
    },
  });
  Object.defineProperty(document, "fonts", {
    configurable: true,
    value: fonts,
  });
  return {
    calls: () => calls,
    listeners: () => attached,
    loadFonts: () => {
      fonts.status = "loaded";
      fonts.dispatchEvent(new Event("loadingdone"));
    },
    restore: () => {
      Reflect.deleteProperty(SVGElement.prototype, "getComputedTextLength");
      Reflect.deleteProperty(document, "fonts");
    },
  };
}
