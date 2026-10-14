import { act, renderHook } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it } from "vitest";

import { raiseCurtain } from "@/components/patterns/ceremony/curtain";
import {
  handOffCeremony,
  pendingCeremony,
} from "@/features/community/api/ceremony-handoff";
import { useHeroCeremony } from "@/features/community/ui/ceremony/use-hero-ceremony";

const community = {
  slug: "backend-guild",
  name: "Backend Guild",
  memberCount: 43,
};

function curtain(): Element | null {
  return document.querySelector("[data-ceremony-curtain]");
}

afterEach(() => {
  curtain()?.remove();
});

describe("useHeroCeremony", () => {
  it("lifts the arrival curtain when no ceremony waits for this community", () => {
    // given
    handOffCeremony("welcome", "design-circle");
    raiseCurtain();

    // when
    renderHook(() => useHeroCeremony(community));

    // then
    expect(curtain()).toBeNull();
  });

  it("leaves the arrival curtain for its own ceremony's stage", () => {
    // given
    handOffCeremony("welcome", "backend-guild");
    raiseCurtain();

    // when
    renderHook(() => useHeroCeremony(community));

    // then
    expect(curtain()).not.toBeNull();
  });

  it("plays nothing without a handoff", () => {
    // when
    const { result } = renderHook(() => useHeroCeremony(community));

    // then
    expect(result.current.active).toBeNull();
  });

  it("welcomes a member handed off from a hub join, counting them already", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    const { result } = renderHook(() => useHeroCeremony(community));

    // then
    expect(result.current.active).toEqual({
      kind: "welcome",
      countFrom: 42,
      countTo: 43,
    });
    expect(result.current.announcement).toBe("Joined Backend Guild");
  });

  it("celebrates a birth handed off from creation", () => {
    // given
    handOffCeremony("birth", "backend-guild");

    // when
    const { result } = renderHook(() => useHeroCeremony(community));

    // then
    expect(result.current.active).toEqual({
      kind: "birth",
      countFrom: 43,
      countTo: 43,
    });
    expect(result.current.announcement).toBe("Backend Guild created");
  });

  it("consumes the handoff so a reload plays nothing", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    renderHook(() => useHeroCeremony(community));

    // then
    expect(pendingCeremony("backend-guild")).toBeNull();
  });

  it("still plays once under StrictMode's double mount", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    const { result } = renderHook(() => useHeroCeremony(community), {
      wrapper: StrictMode,
    });

    // then
    expect(result.current.active?.kind).toBe("welcome");
  });

  it("welcomes a member who just pressed Join, counting from before them", () => {
    // given
    const { result } = renderHook(() => useHeroCeremony(community));

    // when
    act(() => result.current.welcome());

    // then
    expect(result.current.active).toEqual({
      kind: "welcome",
      countFrom: 43,
      countTo: 44,
    });
  });

  it("keeps the announcement once the ceremony is over", () => {
    // given
    const { result } = renderHook(() => useHeroCeremony(community));
    act(() => result.current.welcome());

    // when
    act(() => result.current.finish());

    // then
    expect(result.current.active).toBeNull();
    expect(result.current.announcement).toBe("Joined Backend Guild");
  });

  it("announces a handoff only after the first commit, so a screen reader hears the change", () => {
    // given
    handOffCeremony("birth", "backend-guild");
    const seen: string[] = [];

    // when
    renderHook(() => {
      const hero = useHeroCeremony(community);
      seen.push(hero.announcement);
      return hero;
    });

    // then
    expect(seen[0]).toBe("");
    expect(seen.at(-1)).toBe("Backend Guild created");
  });
});
