import { describe, expect, it } from "vitest";

import { applyCues } from "@/components/patterns/ceremony/cues";
import { birthCues } from "@/features/community/ui/ceremony/birth-choreography";

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
