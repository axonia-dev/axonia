import * as THREE from "three";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Place } from "@/features/expertise/ui/mark-sprites";

/** Muted on purpose: a face that out-shouts the terrain turns the map into a gallery of people. */
const AVATAR_COLOURS = [
  "#6b52c9",
  "#c0562f",
  "#2a9b98",
  "#b58433",
  "#4b74b8",
  "#3a9464",
  "#a8447a",
  "#7d63b5",
];

export function initialsOf(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const first = words[0]![0]!;
  const last = words.length > 1 ? words[words.length - 1]![0]! : "";
  return (first + last).toUpperCase();
}

export function avatarColour(displayName: string): string {
  let hash = 0;
  for (let i = 0; i < displayName.length; i++) {
    hash = (hash * 31 + displayName.charCodeAt(i)) >>> 0;
  }
  return AVATAR_COLOURS[hash % AVATAR_COLOURS.length]!;
}

/**
 * The person's real photo when the directory has one, and their initials when it does not, which is what the rest of
 * the product already draws for a member without a picture.
 */
export function drawFace(
  canvas: HTMLCanvasElement,
  person: MapPerson,
  photo: HTMLImageElement | null,
): void {
  const size = canvas.width;
  const ctx = canvas.getContext("2d");
  if (ctx === null) return;

  ctx.clearRect(0, 0, size, size);
  ctx.save();
  ctx.beginPath();
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
  ctx.clip();

  if (photo === null) {
    ctx.fillStyle = avatarColour(person.displayName);
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = "#ffffff";
    ctx.font = `600 ${size * 0.42}px system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(initialsOf(person.displayName), size / 2, size * 0.54);
  } else {
    ctx.drawImage(photo, 0, 0, size, size);
  }
  ctx.restore();
}

const FACE_TEXTURE_PX = 64;

export interface FaceLayer {
  sprites: THREE.Sprite[];
  dispose: () => void;
}

interface FaceRequest {
  marks: PlacedContribution[];
  faced: ReadonlySet<number>;
  people: ReadonlyMap<string, MapPerson>;
  place: Place;
  /** Called when a photo lands after the layer was drawn, since nothing else would draw it again. */
  repaint: () => void;
}

function faceTexture(
  person: MapPerson,
  alive: () => boolean,
  repaint: () => void,
): THREE.CanvasTexture {
  const canvas = document.createElement("canvas");
  canvas.width = FACE_TEXTURE_PX;
  canvas.height = FACE_TEXTURE_PX;
  drawFace(canvas, person, null);
  const texture = new THREE.CanvasTexture(canvas);
  if (person.avatarUrl === null) return texture;

  const photo = new Image();
  photo.crossOrigin = "anonymous";
  photo.addEventListener("load", () => {
    // A slow photo can land after the scene was torn down, and writing to a disposed texture is a WebGL error
    // rather than a late repaint. An error needs no handler: the initials are already painted.
    if (!alive()) return;
    drawFace(canvas, person, photo);
    texture.needsUpdate = true;
    repaint();
  });
  photo.src = person.avatarUrl;
  return texture;
}

/** A face belongs to a contribution, not to a person, so it is placed at the mark and never anywhere else. */
export function faceSprites(request: Readonly<FaceRequest>): FaceLayer {
  const { marks, faced, people, place, repaint } = request;
  const sprites: THREE.Sprite[] = [];
  const spent: { dispose: () => void }[] = [];
  let alive = true;

  for (const mark of marks) {
    const person = faced.has(mark.signalId)
      ? people.get(mark.userId)
      : undefined;
    if (person === undefined) continue;

    const texture = faceTexture(person, () => alive, repaint);
    // Screen-sized like the marks it stands among: attenuated, a face would grow with every zoom step while the
    // mark beside it held still, and the two would stop reading as one picture. The scale that delivers a size in
    // pixels depends on the lens, so the camera sets it - see `spriteScaleFor`.
    const material = new THREE.SpriteMaterial({
      map: texture,
      sizeAttenuation: false,
    });
    const sprite = new THREE.Sprite(material);
    sprite.position.copy(place(mark.x, mark.y));

    sprites.push(sprite);
    spent.push(texture, material);
  }

  return {
    sprites,
    dispose: () => {
      alive = false;
      for (const item of spent) item.dispose();
    },
  };
}
