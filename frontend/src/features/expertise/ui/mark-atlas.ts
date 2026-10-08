import type { MapContribution } from "@/features/expertise/domain/expertise";
import {
  type Detail,
  type Glyph,
  glyphFor,
} from "@/features/expertise/ui/mark-lod";

/**
 * The images a mark is drawn from: one strip of tiles, white on transparent, which the renderer tints per instance.
 *
 * Apart from `mark-sprites.ts`, which places them in a scene, because the two answer different questions: this one is
 * about what a contribution looks like, that one about where it stands and what colour it takes.
 */
export type AtlasTile = `${Glyph}-icon` | `${Glyph}-shape`;

export const ATLAS_TILE = 64;

export const ATLAS_ORDER: AtlasTile[] = [
  "mic-icon",
  "doc-icon",
  "question-icon",
  "check-icon",
  "mic-shape",
  "doc-shape",
  "question-shape",
  "check-shape",
];

export interface TileRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function tileRect(tile: AtlasTile): TileRect {
  return {
    x: ATLAS_ORDER.indexOf(tile) * ATLAS_TILE,
    y: 0,
    w: ATLAS_TILE,
    h: ATLAS_TILE,
  };
}

export function tileFor(
  sourceType: MapContribution["evidence"]["sourceType"],
  detail: Detail,
): AtlasTile {
  return `${glyphFor(sourceType)}-${detail}`;
}

function drawMic(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.beginPath();
  ctx.roundRect(s * 0.4, s * 0.16, s * 0.2, s * 0.36, s * 0.1);
  ctx.fill();
  ctx.lineWidth = s * 0.07;
  ctx.beginPath();
  ctx.arc(s * 0.5, s * 0.5, s * 0.22, 0, Math.PI);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(s * 0.5, s * 0.72);
  ctx.lineTo(s * 0.5, s * 0.86);
  ctx.stroke();
}

function drawDoc(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.beginPath();
  ctx.moveTo(s * 0.26, s * 0.14);
  ctx.lineTo(s * 0.62, s * 0.14);
  ctx.lineTo(s * 0.76, s * 0.3);
  ctx.lineTo(s * 0.76, s * 0.86);
  ctx.lineTo(s * 0.26, s * 0.86);
  ctx.closePath();
  ctx.fill();
  ctx.globalCompositeOperation = "destination-out";
  ctx.lineWidth = s * 0.07;
  for (const y of [0.46, 0.6, 0.74]) {
    ctx.beginPath();
    ctx.moveTo(s * 0.36, s * y);
    ctx.lineTo(s * 0.66, s * y);
    ctx.stroke();
  }
  ctx.globalCompositeOperation = "source-over";
}

function drawQuestion(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.font = `bold ${s * 0.82}px system-ui, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("?", s * 0.5, s * 0.52);
}

function drawCheck(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.lineWidth = s * 0.09;
  ctx.beginPath();
  ctx.arc(s * 0.5, s * 0.5, s * 0.34, 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(s * 0.33, s * 0.52);
  ctx.lineTo(s * 0.45, s * 0.65);
  ctx.lineTo(s * 0.68, s * 0.37);
  ctx.stroke();
}

/**
 * The four fallbacks below {@link LOD_ICON_PX}. They are chosen for staying distinguishable at four pixels, not for
 * meaning anything: at that size a microphone and a page are the same blob, and a shape that merely differs is worth
 * more than an icon nobody can read.
 */
function drawDot(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.beginPath();
  ctx.arc(s * 0.5, s * 0.5, s * 0.42, 0, Math.PI * 2);
  ctx.fill();
}

function drawSquare(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.fillRect(s * 0.14, s * 0.14, s * 0.72, s * 0.72);
}

function drawDiamond(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.beginPath();
  ctx.moveTo(s * 0.5, s * 0.06);
  ctx.lineTo(s * 0.94, s * 0.5);
  ctx.lineTo(s * 0.5, s * 0.94);
  ctx.lineTo(s * 0.06, s * 0.5);
  ctx.closePath();
  ctx.fill();
}

function drawRing(ctx: CanvasRenderingContext2D, s: number): void {
  ctx.lineWidth = s * 0.24;
  ctx.beginPath();
  ctx.arc(s * 0.5, s * 0.5, s * 0.34, 0, Math.PI * 2);
  ctx.stroke();
}

const PAINTERS: Record<
  AtlasTile,
  (ctx: CanvasRenderingContext2D, s: number) => void
> = {
  "mic-icon": drawMic,
  "doc-icon": drawDoc,
  "question-icon": drawQuestion,
  "check-icon": drawCheck,
  "mic-shape": drawDot,
  "doc-shape": drawSquare,
  "question-shape": drawDiamond,
  "check-shape": drawRing,
};

/** White on transparent: the renderer tints each instance, so one atlas serves every neighbourhood colour. */
export function buildAtlas(): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = ATLAS_TILE * ATLAS_ORDER.length;
  canvas.height = ATLAS_TILE;
  const ctx = canvas.getContext("2d");
  if (ctx === null) return canvas;

  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  for (const tile of ATLAS_ORDER) {
    ctx.save();
    ctx.translate(tileRect(tile).x, 0);
    ctx.fillStyle = "#ffffff";
    ctx.strokeStyle = "#ffffff";
    PAINTERS[tile](ctx, ATLAS_TILE);
    ctx.restore();
  }
  return canvas;
}
