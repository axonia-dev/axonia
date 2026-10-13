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

/**
 * The pixels of an element's text as points relative to `frame`, for sparks to condense onto.
 * Await `loadTextFont` first, or the canvas draws a fallback face.
 */
export function textTargets(
  text: HTMLElement,
  frame: HTMLElement,
  step = 5,
): Point[] {
  const frameBox = frame.getBoundingClientRect();
  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(frameBox.width);
  canvas.height = Math.ceil(frameBox.height);
  const context = canvas.getContext("2d");
  if (context === null || canvas.width === 0 || canvas.height === 0) {
    return [];
  }
  const style = getComputedStyle(text);
  const scale = frameBox.width / (frame.offsetWidth || frameBox.width);
  context.font = fontOf(style, scale);
  context.letterSpacing = style.letterSpacing;
  context.textBaseline = "middle";
  context.fillStyle = "#ffffff";
  for (const fragment of textFragments(text)) {
    drawFitted(context, fragment, frameBox);
  }
  return sampleTargets(
    context.getImageData(0, 0, canvas.width, canvas.height),
    step,
  );
}
