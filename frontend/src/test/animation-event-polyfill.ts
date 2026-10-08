// jsdom (as of v30) does not implement the AnimationEvent constructor. react-dom checks
// `"AnimationEvent" in window` once, at module-load time, to decide whether it can listen
// for the unprefixed "animationend"/"animationstart"/"animationiteration" events at all -
// without this, fireEvent.animationEnd() in any test never reaches an onAnimationEnd handler.
// This file has no imports of its own so it always finishes before react-dom's first import
// elsewhere in the setup file runs its module-load check.
const untypedWindow = window as unknown as Record<string, unknown>;

if (typeof untypedWindow.AnimationEvent === "undefined") {
  class AnimationEventPolyfill extends Event {
    readonly animationName: string;
    readonly elapsedTime: number;
    readonly pseudoElement: string;

    constructor(type: string, eventInitDict: AnimationEventInit = {}) {
      super(type, eventInitDict);
      this.animationName = eventInitDict.animationName ?? "";
      this.elapsedTime = eventInitDict.elapsedTime ?? 0;
      this.pseudoElement = eventInitDict.pseudoElement ?? "";
    }
  }

  untypedWindow.AnimationEvent = AnimationEventPolyfill;
}
