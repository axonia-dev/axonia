import type { Page } from "@playwright/test";

/** How many distinct colours a screenshot holds, decoded in the page because Node has no PNG decoder here. */
export async function paintedColours(
  shot: Buffer,
  page: Page,
): Promise<number> {
  return page.evaluate(async (encoded) => {
    const blob = await (await fetch(`data:image/png;base64,${encoded}`)).blob();
    const bitmap = await createImageBitmap(blob);
    const board = document.createElement("canvas");
    board.width = bitmap.width;
    board.height = bitmap.height;
    const ctx = board.getContext("2d");
    if (ctx === null) return 0;
    ctx.drawImage(bitmap, 0, 0);
    const { data } = ctx.getImageData(0, 0, board.width, board.height);
    const seen = new Set<string>();
    for (let i = 0; i < data.length; i += 4) {
      seen.add(`${data[i]},${data[i + 1]},${data[i + 2]}`);
      if (seen.size > 1) return seen.size;
    }
    return seen.size;
  }, shot.toString("base64"));
}

/**
 * How many pixels differ between two screenshots of the same size. A pixel counts only when a channel moves by more
 * than a few levels, because two frames of a WebGL canvas never agree to the last bit (dithering, antialiasing).
 */
export async function differingPixels(
  a: Buffer,
  b: Buffer,
  page: Page,
): Promise<number> {
  return page.evaluate(
    async ([first, second]) => {
      async function decode(encoded: string) {
        const blob = await (
          await fetch(`data:image/png;base64,${encoded}`)
        ).blob();
        const bitmap = await createImageBitmap(blob);
        const board = document.createElement("canvas");
        board.width = bitmap.width;
        board.height = bitmap.height;
        const ctx = board.getContext("2d")!;
        ctx.drawImage(bitmap, 0, 0);
        return ctx.getImageData(0, 0, board.width, board.height).data;
      }
      const [x, y] = await Promise.all([decode(first), decode(second)]);
      if (x.length !== y.length) return x.length;
      let count = 0;
      for (let i = 0; i < x.length; i += 4) {
        const moved =
          Math.abs(x[i] - y[i]) > 8 ||
          Math.abs(x[i + 1] - y[i + 1]) > 8 ||
          Math.abs(x[i + 2] - y[i + 2]) > 8;
        if (moved) count++;
      }
      return count;
    },
    [a.toString("base64"), b.toString("base64")],
  );
}
