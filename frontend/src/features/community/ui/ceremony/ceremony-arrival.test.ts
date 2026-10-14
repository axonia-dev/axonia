import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  handOffCeremony,
  pendingCeremony,
} from "@/features/community/api/ceremony-handoff";
import {
  departForCeremony,
  prepareCeremonyArrival,
} from "@/features/community/ui/ceremony/ceremony-arrival";

const { preloadHeroCeremony } = vi.hoisted(() => ({
  preloadHeroCeremony: vi.fn(),
}));
vi.mock("@/features/community/ui/ceremony/lazy-ceremonies", () => ({
  preloadHeroCeremony,
}));

function curtain(): Element | null {
  return document.querySelector("[data-ceremony-curtain]");
}

beforeEach(() => {
  document.documentElement.classList.add("ceremony-arrival");
});

afterEach(() => {
  curtain()?.remove();
  document.documentElement.classList.remove("ceremony-arrival");
  preloadHeroCeremony.mockClear();
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
  document.getElementById("root")?.remove();
});

describe("prepareCeremonyArrival", () => {
  it("raises the curtain and fetches the ceremony when one was handed off", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).not.toBeNull();
    expect(preloadHeroCeremony).toHaveBeenCalledOnce();
    expect(document.documentElement).not.toHaveClass("ceremony-arrival");
  });

  it("leaves the page alone when nothing was handed off", () => {
    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).toBeNull();
    expect(preloadHeroCeremony).not.toHaveBeenCalled();
    expect(document.documentElement).not.toHaveClass("ceremony-arrival");
  });

  it("raises no curtain under reduced motion, where no stage plays", () => {
    // given
    handOffCeremony("welcome", "backend-guild");
    vi.spyOn(window, "matchMedia").mockReturnValue({
      matches: true,
    } as MediaQueryList);

    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).toBeNull();
    expect(document.documentElement).not.toHaveClass("ceremony-arrival");
  });

  it("raises the curtain on the community the ceremony waits for", () => {
    // given
    handOffCeremony("welcome", "backend-guild");
    window.history.pushState({}, "", "/communities/backend-guild");

    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).not.toBeNull();
  });

  it("raises no curtain on a page the ceremony does not play on", () => {
    // given
    handOffCeremony("welcome", "backend-guild");
    window.history.pushState({}, "", "/discover");

    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).toBeNull();
    expect(document.documentElement).not.toHaveClass("ceremony-arrival");
  });

  it("raises no curtain over a prerendered page", () => {
    // given
    handOffCeremony("welcome", "backend-guild");
    const root = document.createElement("div");
    root.id = "root";
    root.append(document.createElement("main"));
    document.body.append(root);

    // when
    prepareCeremonyArrival();

    // then
    expect(curtain()).toBeNull();
  });
});

describe("departForCeremony", () => {
  it("hands the ceremony off and is fully dark before the app navigates", async () => {
    // when
    await departForCeremony("welcome", "backend-guild");

    // then
    expect(pendingCeremony("backend-guild")).toBe("welcome");
    expect(curtain()).not.toBeNull();
    expect(preloadHeroCeremony).toHaveBeenCalledOnce();
  });

  it("darkens nothing under reduced motion, where no stage plays", async () => {
    // given
    vi.spyOn(window, "matchMedia").mockReturnValue({
      matches: true,
    } as MediaQueryList);

    // when
    await departForCeremony("birth", "backend-guild");

    // then
    expect(pendingCeremony("backend-guild")).toBe("birth");
    expect(curtain()).toBeNull();
  });
});
