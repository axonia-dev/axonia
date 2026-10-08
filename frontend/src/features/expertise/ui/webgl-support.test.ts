import { afterEach, expect, test, vi } from "vitest";

/** The answer is remembered for the life of the document, so each case needs the module loaded afresh. */
async function support(): Promise<() => boolean> {
  vi.resetModules();
  const module = await import("@/features/expertise/ui/webgl-support");
  return module.hasWebGl2;
}

afterEach(() => {
  vi.restoreAllMocks();
});

test("is false in an environment with no WebGL2 context, such as jsdom", async () => {
  expect((await support())()).toBe(false);
});

test("is true when the canvas hands one back", async () => {
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    getExtension: () => null,
  } as unknown as RenderingContext);

  expect((await support())()).toBe(true);
});

test("is false when asking for a context throws", async () => {
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(() => {
    throw new Error("blocked");
  });

  expect((await support())()).toBe(false);
});

/** A probe that kept its context would spend the browser's budget of them, and the one evicted is the drawing's. */
test("hands the probe context back before answering", async () => {
  const loseContext = vi.fn();
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    getExtension: (name: string) =>
      name === "WEBGL_lose_context" ? { loseContext } : null,
  } as unknown as RenderingContext);

  (await support())();

  expect(loseContext).toHaveBeenCalledOnce();
});

test("asks the browser once, however many times it is called", async () => {
  const getContext = vi
    .spyOn(HTMLCanvasElement.prototype, "getContext")
    .mockReturnValue({
      getExtension: () => null,
    } as unknown as RenderingContext);
  const hasWebGl2 = await support();

  for (let call = 0; call < 5; call++) hasWebGl2();

  expect(getContext).toHaveBeenCalledOnce();
});
