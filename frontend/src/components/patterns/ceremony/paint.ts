import type { Field, Spark } from "./spark-field";

export type Painter = (
  context: CanvasRenderingContext2D,
  t: number,
  card: HTMLElement,
) => void;

export function viewportField(): Field {
  const root = document.documentElement;
  return {
    width: root.clientWidth || window.innerWidth,
    height: root.clientHeight || window.innerHeight,
  };
}

/**
 * A colour as the canvas can parse it. A canvas silently ignores a `fillStyle` it cannot read, such as
 * a custom property, so the browser resolves the colour once, inside `scope` where any variable is defined.
 */
export function canvasColour(colour: string, scope: Element): string {
  const probe = document.createElement("span");
  probe.style.color = colour;
  probe.style.display = "none";
  scope.append(probe);
  const resolved = getComputedStyle(probe).color;
  probe.remove();
  return resolved === "" ? colour : resolved;
}

export function drawSparks(
  context: CanvasRenderingContext2D,
  sparks: readonly Spark[],
  hue: string,
): void {
  context.save();
  context.globalCompositeOperation = "lighter";
  for (const spark of sparks) {
    context.globalAlpha = spark.alpha;
    context.fillStyle = spark.white ? "#ffffff" : hue;
    context.beginPath();
    context.arc(spark.x, spark.y, spark.size, 0, Math.PI * 2);
    context.fill();
  }
  context.restore();
}

function band(
  context: CanvasRenderingContext2D,
  x: number,
  halfWidth: number,
  colour: string,
): CanvasGradient {
  const gradient = context.createLinearGradient(
    x - halfWidth,
    0,
    x + halfWidth,
    0,
  );
  gradient.addColorStop(0, "transparent");
  gradient.addColorStop(0.5, colour);
  gradient.addColorStop(1, "transparent");
  return gradient;
}

export interface LightBar {
  readonly x: number;
  readonly top: number;
  readonly height: number;
}

/** A vertical edge of light: a wide coloured halo around a narrow white core. */
export function drawLightBar(
  context: CanvasRenderingContext2D,
  bar: LightBar,
  hue: string,
  alpha: number,
): void {
  if (alpha <= 0) {
    return;
  }
  const { x, top, height } = bar;
  context.save();
  context.globalCompositeOperation = "lighter";
  context.globalAlpha = alpha * 0.4;
  context.fillStyle = band(context, x, 160, hue);
  context.fillRect(x - 160, top, 320, height);
  context.globalAlpha = alpha * 0.9;
  context.fillStyle = band(context, x, 40, "#ffffff");
  context.fillRect(x - 40, top, 80, height);
  context.restore();
}
