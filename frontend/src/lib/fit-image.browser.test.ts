import { afterEach, describe, expect, it, vi } from "vitest";

import { browserCodec, fitImage } from "@/lib/fit-image";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("browserCodec", () => {
  it("returns the original file where the browser has no decoder", async () => {
    // given
    const file = new File(["original"], "banner.png", { type: "image/png" });
    vi.stubGlobal("createImageBitmap", undefined);

    // when
    const actual = await fitImage(file, { maxEdge: 512, maxBytes: 1024 });

    // then
    expect(actual).toBe(file);
  });

  it("decodes through createImageBitmap", async () => {
    // given
    const bitmap = { width: 1, height: 1, close: vi.fn() };
    const createImageBitmap = vi.fn(() => Promise.resolve(bitmap));
    vi.stubGlobal("createImageBitmap", createImageBitmap);
    const file = new File(["original"], "banner.png", { type: "image/png" });

    // when
    const actual = await browserCodec.decode(file);

    // then
    expect(actual).toBe(bitmap);
    expect(createImageBitmap).toHaveBeenCalledWith(file);
  });

  it("draws the image smoothed at the fitted size and encodes it in the asked type", async () => {
    // given
    const encoded = new Blob(["small"], { type: "image/jpeg" });
    const context = { imageSmoothingQuality: "low", drawImage: vi.fn() };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as never,
    );
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, "toBlob")
      .mockImplementation((callback) => callback(encoded));
    const image = {} as ImageBitmap;

    // when
    const actual = await browserCodec.encode(
      image,
      { width: 300, height: 200 },
      "image/jpeg",
    );

    // then
    expect(actual).toBe(encoded);
    expect(context.imageSmoothingQuality).toBe("high");
    expect(context.drawImage).toHaveBeenCalledWith(image, 0, 0, 300, 200);
    expect(toBlob).toHaveBeenCalledWith(
      expect.any(Function),
      "image/jpeg",
      0.9,
    );
  });

  it("encodes nothing where the canvas has no 2D context", async () => {
    // when
    const actual = await browserCodec.encode(
      {} as ImageBitmap,
      { width: 300, height: 200 },
      "image/png",
    );

    // then
    expect(actual).toBeNull();
  });
});
