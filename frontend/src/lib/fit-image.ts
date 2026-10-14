export interface Size {
  readonly width: number;
  readonly height: number;
}

export interface DecodedImage extends Size {
  close(): void;
}

/** The browser pieces fitImageWith needs, a seam because jsdom has neither a decoder nor a canvas. */
export interface ImageCodec<T extends DecodedImage> {
  decode(file: File): Promise<T>;
  encode(image: T, size: Size, type: string): Promise<Blob | null>;
}

const ENCODE_QUALITY = 0.9;
const WEBP = "image/webp";

const EXTENSIONS = new Map([
  ["image/png", "png"],
  ["image/jpeg", "jpg"],
  [WEBP, "webp"],
]);

/** The size that brings the longest edge down to `maxEdge`, or null when it already fits. */
export function fittedSize(image: Size, maxEdge: number): Size | null {
  const longest = Math.max(image.width, image.height);
  if (longest <= maxEdge) return null;
  const scale = maxEdge / longest;
  return {
    width: Math.max(1, Math.round(image.width * scale)),
    height: Math.max(1, Math.round(image.height * scale)),
  };
}

/** `name` with the extension of `type`, or unchanged when `type` is one it already carries. */
export function nameFor(name: string, originalType: string, type: string) {
  const extension = EXTENSIONS.get(type);
  if (type === originalType || extension === undefined) return name;
  const dot = name.lastIndexOf(".");
  const base = dot > 0 ? name.slice(0, dot) : name;
  return `${base}.${extension}`;
}

export const browserCodec: ImageCodec<ImageBitmap> = {
  decode: (file) => createImageBitmap(file),
  encode: (image, size, type) =>
    new Promise((resolve) => {
      const canvas = document.createElement("canvas");
      canvas.width = size.width;
      canvas.height = size.height;
      const context = canvas.getContext("2d");
      if (context === null) {
        resolve(null);
        return;
      }
      context.imageSmoothingQuality = "high";
      context.drawImage(image, 0, 0, size.width, size.height);
      canvas.toBlob(resolve, type, ENCODE_QUALITY);
    }),
};

export interface ImageLimits {
  readonly maxEdge: number;
  readonly maxBytes: number;
}

interface EncodeJob<T extends DecodedImage> {
  readonly image: T;
  readonly size: Size;
  readonly type: string;
  readonly maxBytes: number;
  readonly codec: ImageCodec<T>;
}

function candidateTypes(type: string) {
  return type === WEBP ? [WEBP] : [type, WEBP];
}

/** The first encoding within `maxBytes`, trying the original type before WebP, else the
 *  smallest one the server accepts, or null when the browser produced none it accepts. */
async function encodeWithin<T extends DecodedImage>(job: EncodeJob<T>) {
  let smallest: Blob | null = null;
  for (const type of candidateTypes(job.type)) {
    const blob = await job.codec.encode(job.image, job.size, type);
    if (blob === null || !EXTENSIONS.has(blob.type)) continue;
    if (blob.size <= job.maxBytes) return blob;
    if (smallest === null || blob.size < smallest.size) smallest = blob;
  }
  return smallest;
}

async function shrink<T extends DecodedImage>(
  file: File,
  limits: ImageLimits,
  codec: ImageCodec<T>,
): Promise<File> {
  const image = await codec.decode(file);
  try {
    const size = fittedSize(image, limits.maxEdge);
    if (size === null) return file;
    const { maxBytes } = limits;
    const blob = await encodeWithin({
      image,
      size,
      type: file.type,
      maxBytes,
      codec,
    });
    if (blob === null) return file;
    return new File([blob], nameFor(file.name, file.type, blob.type), {
      type: blob.type,
      lastModified: file.lastModified,
    });
  } finally {
    image.close();
  }
}

/** `file` scaled down so its longest edge is at most `maxEdge`, re-encoded as WebP when its
 *  own type comes out over `maxBytes`. `file` itself when it already fits or cannot be
 *  decoded, leaving the refusal to validation and the server. */
export async function fitImageWith<T extends DecodedImage>(
  file: File,
  limits: ImageLimits,
  codec: ImageCodec<T>,
): Promise<File> {
  try {
    return await shrink(file, limits, codec);
  } catch {
    return file;
  }
}

export function fitImage(file: File, limits: ImageLimits) {
  return fitImageWith(file, limits, browserCodec);
}
