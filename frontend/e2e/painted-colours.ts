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
