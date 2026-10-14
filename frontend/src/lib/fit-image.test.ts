import { describe, expect, it, vi } from "vitest";

import type { DecodedImage, ImageCodec, Size } from "@/lib/fit-image";
import { fitImageWith, fittedSize, nameFor } from "@/lib/fit-image";

const LIMITS = { maxEdge: 512, maxBytes: 10 };

function photo(name = "banner.png", type = "image/png") {
  return new File(["original"], name, { type, lastModified: 42 });
}

function blobOf(bytes: number, type: string) {
  return new Blob([new Uint8Array(bytes)], { type });
}

/** A codec decoding to `size` that encodes each asked type to `encodings[type]`. */
function fakeCodec(size: Size, encodings: Record<string, Blob | null> = {}) {
  const close = vi.fn();
  const encode = vi.fn<ImageCodec<DecodedImage>["encode"]>((_, __, type) =>
    Promise.resolve(new Map(Object.entries(encodings)).get(type) ?? null),
  );
  const codec: ImageCodec<DecodedImage> = {
    decode: () => Promise.resolve({ ...size, close }),
    encode,
  };
  return { codec, close, encode };
}

const LARGE = { width: 4000, height: 4000 };

describe("fittedSize", () => {
  it("brings a landscape image's width down to the maximum, keeping its aspect", () => {
    // when
    const actual = fittedSize({ width: 10087, height: 7594 }, 2560);

    // then
    expect(actual).toEqual({ width: 2560, height: 1927 });
  });

  it("brings a portrait image's height down to the maximum", () => {
    // when
    const actual = fittedSize({ width: 3000, height: 4000 }, 512);

    // then
    expect(actual).toEqual({ width: 384, height: 512 });
  });

  it("never shrinks an edge below one pixel", () => {
    // when
    const actual = fittedSize({ width: 10000, height: 1 }, 256);

    // then
    expect(actual).toEqual({ width: 256, height: 1 });
  });

  it("leaves an image whose longest edge is exactly the maximum", () => {
    // when
    const actual = fittedSize({ width: 512, height: 300 }, 512);

    // then
    expect(actual).toBeNull();
  });
});

describe("nameFor", () => {
  it("keeps the name when the type did not change", () => {
    // when
    const actual = nameFor("logo.PNG", "image/png", "image/png");

    // then
    expect(actual).toBe("logo.PNG");
  });

  it("swaps the extension for the type the browser fell back to", () => {
    // when
    const actual = nameFor("my.logo.webp", "image/webp", "image/png");

    // then
    expect(actual).toBe("my.logo.png");
  });

  it("adds an extension to a name that has none", () => {
    // when
    const actual = nameFor("logo", "image/webp", "image/jpeg");

    // then
    expect(actual).toBe("logo.jpg");
  });

  it("keeps the name for a type it has no extension for", () => {
    // when
    const actual = nameFor("logo.png", "image/png", "image/avif");

    // then
    expect(actual).toBe("logo.png");
  });
});

describe("fitImageWith", () => {
  it("returns the original file untouched when it already fits", async () => {
    // given
    const file = photo();
    const { codec, close, encode } = fakeCodec({ width: 512, height: 300 });

    // when
    const actual = await fitImageWith(file, LIMITS, codec);

    // then
    expect(actual).toBe(file);
    expect(encode).not.toHaveBeenCalled();
    expect(close).toHaveBeenCalledOnce();
  });

  it("encodes an oversized image at the fitted size in its own type", async () => {
    // given
    const { codec, encode } = fakeCodec(
      { width: 10087, height: 7594 },
      { "image/png": blobOf(5, "image/png") },
    );

    // when
    await fitImageWith(photo(), { maxEdge: 2560, maxBytes: 10 }, codec);

    // then
    expect(encode).toHaveBeenCalledExactlyOnceWith(
      expect.anything(),
      { width: 2560, height: 1927 },
      "image/png",
    );
  });

  it("returns the encoded file under the original name and modification time", async () => {
    // given
    const encoded = blobOf(5, "image/png");
    const { codec, close } = fakeCodec(LARGE, { "image/png": encoded });

    // when
    const actual = await fitImageWith(photo(), LIMITS, codec);

    // then
    expect(actual).toMatchObject({
      name: "banner.png",
      type: "image/png",
      size: 5,
      lastModified: 42,
    });
    expect(close).toHaveBeenCalledOnce();
  });

  it("names the file after the type the browser actually encoded", async () => {
    // given
    const { codec } = fakeCodec(LARGE, {
      "image/webp": blobOf(5, "image/png"),
    });

    // when
    const actual = await fitImageWith(
      photo("face.webp", "image/webp"),
      LIMITS,
      codec,
    );

    // then
    expect(actual).toMatchObject({ name: "face.png", type: "image/png" });
  });

  it("re-encodes as WebP when its own type comes out over the byte budget", async () => {
    // given
    const { codec } = fakeCodec(LARGE, {
      "image/png": blobOf(20, "image/png"),
      "image/webp": blobOf(8, "image/webp"),
    });

    // when
    const actual = await fitImageWith(photo(), LIMITS, codec);

    // then
    expect(actual).toMatchObject({
      name: "banner.webp",
      type: "image/webp",
      size: 8,
    });
  });

  it("keeps the smallest encoding when none fits the byte budget", async () => {
    // given
    const { codec } = fakeCodec(LARGE, {
      "image/png": blobOf(15, "image/png"),
      "image/webp": blobOf(30, "image/webp"),
    });

    // when
    const actual = await fitImageWith(photo(), LIMITS, codec);

    // then
    expect(actual).toMatchObject({ type: "image/png", size: 15 });
  });

  it("skips an encoding the browser fell back to a type the server refuses", async () => {
    // given
    const { codec } = fakeCodec(LARGE, {
      "image/png": blobOf(20, "image/png"),
      "image/webp": blobOf(1, "image/bmp"),
    });

    // when
    const actual = await fitImageWith(photo(), LIMITS, codec);

    // then
    expect(actual).toMatchObject({ type: "image/png", size: 20 });
  });

  it("tries WebP once for a WebP image over the byte budget", async () => {
    // given
    const { codec, encode } = fakeCodec(LARGE, {
      "image/webp": blobOf(20, "image/webp"),
    });

    // when
    await fitImageWith(photo("face.webp", "image/webp"), LIMITS, codec);

    // then
    expect(encode).toHaveBeenCalledOnce();
  });

  it("returns the original file when the browser encodes nothing it accepts", async () => {
    // given
    const file = photo();
    const { codec, close } = fakeCodec(LARGE, {
      "image/webp": blobOf(1, "image/bmp"),
    });

    // when
    const actual = await fitImageWith(file, LIMITS, codec);

    // then
    expect(actual).toBe(file);
    expect(close).toHaveBeenCalledOnce();
  });

  it("returns the original file when it cannot be decoded", async () => {
    // given
    const file = photo();
    const codec: ImageCodec<DecodedImage> = {
      decode: () => Promise.reject(new Error("not an image")),
      encode: vi.fn(),
    };

    // when
    const actual = await fitImageWith(file, LIMITS, codec);

    // then
    expect(actual).toBe(file);
  });
});
