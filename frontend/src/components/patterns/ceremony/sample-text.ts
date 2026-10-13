import { type Point, sampleTargets } from "./spark-field";

interface WordBox {
  readonly word: string;
  readonly box: DOMRect;
}

/** Each word of the element's text with its own line box, so a wrapped name keeps its lines. */
function wordBoxes(text: HTMLElement): WordBox[] {
  const node = text.firstChild;
  if (!(node instanceof Text)) {
    return [];
  }
  const range = document.createRange();
  return [...node.data.matchAll(/\S+/g)].map((match) => {
    range.setStart(node, match.index);
    range.setEnd(node, match.index + match[0].length);
    return { word: match[0], box: range.getBoundingClientRect() };
  });
}

/** The pixels of an element's text as points relative to `frame`, for sparks to condense onto. */
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
  context.font = `${style.fontWeight} ${parseFloat(style.fontSize) * scale}px ${style.fontFamily}`;
  context.textBaseline = "middle";
  context.fillStyle = "#ffffff";
  for (const { word, box } of wordBoxes(text)) {
    context.fillText(
      word,
      box.left - frameBox.left,
      box.top - frameBox.top + box.height / 2,
    );
  }
  return sampleTargets(
    context.getImageData(0, 0, canvas.width, canvas.height),
    step,
  );
}
