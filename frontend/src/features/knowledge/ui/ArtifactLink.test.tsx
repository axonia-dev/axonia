import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as recordOpeningModule from "@/features/knowledge/api/record-opening";
import { ArtifactLink } from "@/features/knowledge/ui/ArtifactLink";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ArtifactLink", () => {
  it("keeps the real destination on the anchor", () => {
    render(
      <ArtifactLink artifactId={42} url="https://example.test/talk">
        A talk
      </ArtifactLink>,
    );

    const link = screen.getByRole("link", { name: "A talk" });
    expect(link).toHaveAttribute("href", "https://example.test/talk");
    // The beacon is a fire-and-forget fetch, not sendBeacon: it only survives because the
    // click always opens a new tab and the current page never unloads. Losing target="_blank"
    // turns every opening into a race against navigation.
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noreferrer");
  });

  it("counts the opening on a click", async () => {
    const spy = vi
      .spyOn(recordOpeningModule, "recordOpening")
      .mockImplementation(() => undefined);

    render(
      <ArtifactLink artifactId={42} url="https://example.test/talk">
        A talk
      </ArtifactLink>,
    );
    await user.click(screen.getByRole("link", { name: "A talk" }));

    expect(spy).toHaveBeenCalledWith(42);
  });

  // Never calling preventDefault is the rule the whole design rests on: the href stays
  // the real navigation, and the beacon rides alongside it rather than gating it. The
  // handler still runs under the spy, so it does not affect this assertion; the spy is
  // here only to keep the dispatched click from reaching the real gateway and firing a
  // network request against the real backend.
  it("never blocks the navigation the click would otherwise perform", () => {
    vi.spyOn(recordOpeningModule, "recordOpening").mockImplementation(
      () => undefined,
    );

    render(
      <ArtifactLink artifactId={42} url="https://example.test/talk">
        A talk
      </ArtifactLink>,
    );
    const link = screen.getByRole("link", { name: "A talk" });
    const event = new MouseEvent("click", {
      bubbles: true,
      cancelable: true,
    });
    link.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(false);
  });

  it("counts a middle click but not a right click", async () => {
    const spy = vi
      .spyOn(recordOpeningModule, "recordOpening")
      .mockImplementation(() => undefined);

    render(
      <ArtifactLink artifactId={42} url="https://example.test/talk">
        A talk
      </ArtifactLink>,
    );
    const link = screen.getByRole("link", { name: "A talk" });
    await user.pointer({ target: link, keys: "[MouseRight]" });
    expect(spy).not.toHaveBeenCalled();

    await user.pointer({ target: link, keys: "[MouseMiddle]" });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(42);
  });
});
