import { type Point, sampleTargets } from "./spark-field";

interface Fragment {
  readonly text: string;
  readonly box: DOMRect;
}

function fragmentsOf(node: Text, range: Range, start: number, end: number) {
  range.setStart(node, start);
  range.setEnd(node, end);
  const rects = range.getClientRects();
  if (rects.length <= 1) {
    return [
      { text: node.data.slice(start, end), box: range.getBoundingClientRect() },
    ];
  }
  return [...Array(end - start).keys()].map((offset) => {
    range.setStart(node, start + offset);
    range.setEnd(node, start + offset + 1);
    return {
      text: node.data.charAt(start + offset),
      box: range.getBoundingClientRect(),
    };
  });
}

/** Each word with its own box, so a wrapped name keeps its lines; a word the browser broke is split per character. */
function textFragments(text: HTMLElement): Fragment[] {
  const node = text.firstChild;
  if (!(node instanceof Text)) {
    return [];
  }
  const range = document.createRange();
  return [...node.data.matchAll(/\S+/g)].flatMap((match) =>
    fragmentsOf(node, range, match.index, match.index + match[0].length),
  );
}

function fontOf(style: CSSStyleDeclaration, scale: number): string {
  const size = parseFloat(style.fontSize) * scale;
  return `${style.fontStyle} ${style.fontWeight} ${size}px ${style.fontFamily}`;
}

/** Resolves once the element's font is available, so sampling draws the glyphs the page shows. */
export async function loadTextFont(text: HTMLElement): Promise<void> {
  const fonts = document.fonts as FontFaceSet | undefined;
  await fonts?.load(fontOf(getComputedStyle(text), 1), text.textContent ?? "");
}

function drawFitted(
  context: CanvasRenderingContext2D,
  { text, box }: Fragment,
  origin: DOMRect,
): void {
  const natural = context.measureText(text).width;
  context.save();
  context.translate(
    box.left - origin.left,
    box.top - origin.top + box.height / 2,
  );
  context.scale(natural > 0 ? box.width / natural : 1, 1);
  context.fillText(text, 0, 0);
  context.restore();
}

/** Samples per em of the text's rendered size: dense enough for the letters to read at any size. */
const STEP_PER_EM = 0.075;
const MIN_STEP = 1.5;

function scaleOf(frame: HTMLElement): number {
  const width = frame.getBoundingClientRect().width;
  return width / (frame.offsetWidth || width || 1);
}

/** The distance between samples for an element's text as `frame` draws it, in screen pixels. */
export function sampleStep(text: HTMLElement, frame: HTMLElement): number {
  const size = parseFloat(getComputedStyle(text).fontSize) || 16;
  return Math.max(MIN_STEP, size * scaleOf(frame) * STEP_PER_EM);
}

/**
 * The pixels of an element's text as points relative to `frame`, `step` screen pixels apart, for
 * sparks to condense onto. It draws at the device's pixel density, so a small name keeps its shapes. Await `loadTextFont` first, or the canvas draws a fallback face.
 */
export function textTargets(
  text: HTMLElement,
  frame: HTMLElement,
  step: number,
): Point[] {
  const frameBox = frame.getBoundingClientRect();
  const density = Math.max(1, window.devicePixelRatio || 1);
  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(frameBox.width * density);
  canvas.height = Math.ceil(frameBox.height * density);
  const context = canvas.getContext("2d");
  if (context === null || canvas.width === 0 || canvas.height === 0) {
    return [];
  }
  context.setTransform(density, 0, 0, density, 0, 0);
  const style = getComputedStyle(text);
  context.font = fontOf(style, scaleOf(frame));
  context.letterSpacing = style.letterSpacing;
  context.textBaseline = "middle";
  context.fillStyle = "#ffffff";
  for (const fragment of textFragments(text)) {
    drawFitted(context, fragment, frameBox);
  }
  return sampleTargets(
    context.getImageData(0, 0, canvas.width, canvas.height),
    step * density,
  ).map(({ x, y }) => ({ x: x / density, y: y / density }));
}
