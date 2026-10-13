import { describe, expect, it, vi } from "vitest";

import { applyCues } from "@/components/patterns/ceremony/cues";
import {
  loadTextFont,
  textTargets,
} from "@/components/patterns/ceremony/sample-text";
import {
  birthCues,
  birthPainter,
} from "@/features/community/ui/ceremony/birth-choreography";

vi.mock("@/components/patterns/ceremony/sample-text", () => ({
  loadTextFont: vi.fn(),
  sampleStep: vi.fn(() => 5),
  textTargets: vi.fn(() => []),
}));
vi.mock("@/components/patterns/ceremony/paint", async (original) => ({
  ...(await original<object>()),
  drawSparks: vi.fn(),
}));

function card(): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = `
    <div data-ceremony-part="surface">
      <div data-ceremony-part="meta"></div>
      <h1 data-ceremony-part="name">Platform Engineering</h1>
      <div data-ceremony-part="footer"></div>
    </div>`;
  return root;
}

function opacityOf(root: HTMLElement, name: string): string {
  return (
    root.querySelector<HTMLElement>(`[data-ceremony-part="${name}"]`)?.style
      .opacity ?? ""
  );
}

describe("birthCues", () => {
  it("keeps the card dark while the sparks gather", () => {
    // given
    const root = card();

    // when
    applyCues(root, birthCues("#8b5cf6"), 1.5);

    // then
    expect(opacityOf(root, "surface")).toBe("0");
    expect(opacityOf(root, "name")).toBe("0");
  });

  it("resolves the name from the sparks", () => {
    // given
    const root = card();

    // when
    applyCues(root, birthCues("#8b5cf6"), 2.6);

    // then
    expect(opacityOf(root, "name")).toBe("1");
    expect(opacityOf(root, "meta")).toBe("0");
  });

  it("ends with every part in place and no glint", () => {
    // given
    const root = card();

    // when
    applyCues(root, birthCues("#8b5cf6"), 3.4);

    // then
    expect(opacityOf(root, "meta")).toBe("1");
    expect(opacityOf(root, "footer")).toBe("1");
    expect(
      root.querySelector<HTMLElement>('[data-ceremony-part="name"]')?.style
        .color,
    ).toBe("");
  });
});

describe("birthPainter", () => {
  const context = {} as CanvasRenderingContext2D;

  it("samples the name only once its font has loaded, and loads it once", async () => {
    // given
    let loaded: () => void = () => {};
    vi.mocked(loadTextFont).mockReturnValue(
      new Promise<void>((resolve) => {
        loaded = resolve;
      }),
    );
    const paint = birthPainter("#8b5cf6");
    const root = card();

    // when
    paint(context, 0.5, root);
    paint(context, 0.6, root);
    const before = vi.mocked(textTargets).mock.calls.length;
    loaded();
    await Promise.resolve();
    await Promise.resolve();

    // then
    expect(loadTextFont).toHaveBeenCalledOnce();
    expect(before).toBe(0);
    expect(textTargets).toHaveBeenCalledOnce();
  });

  it("still condenses onto the fallback face when the font fails", async () => {
    // given
    vi.mocked(loadTextFont).mockRejectedValue(new Error("offline"));
    vi.mocked(textTargets).mockClear();
    const paint = birthPainter("#8b5cf6");

    // when
    paint(context, 0.5, card());
    await new Promise((resolve) => setTimeout(resolve));

    // then
    expect(textTargets).toHaveBeenCalledOnce();
  });
});
