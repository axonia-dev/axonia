import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  armMorph,
  createMorphHistory,
  disarmMorph,
  MORPH,
  MORPH_BACK,
  replayTypes,
} from "@/components/patterns/morph/morph-history";

const STORE = "axonia.morphs";

function allowMorphs() {
  Object.assign(document, { startViewTransition: vi.fn() });
  vi.stubGlobal("matchMedia", () => ({ matches: false }));
}

describe("createMorphHistory", () => {
  beforeEach(() => {
    sessionStorage.clear();
    allowMorphs();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, "startViewTransition");
    disarmMorph();
  });

  it("records the entries an armed push connects, and only those", () => {
    // given
    const history = createMorphHistory();
    const seen: string[] = [];
    history.listen(({ location }) => seen.push(location.key));
    const from = history.location.key;

    // when
    history.push("/plain");
    armMorph();
    history.push("/morphed");
    disarmMorph();

    // then
    const to = seen[1]!;
    expect(replayTypes(seen[0]!, to)).toEqual([MORPH]);
    expect(replayTypes(to, seen[0]!)).toEqual([MORPH, MORPH_BACK]);
    expect(replayTypes(from, seen[0]!)).toEqual([]);
  });

  it("passes a plain pop straight through, in the same task", () => {
    // given
    const history = createMorphHistory();
    const listener = vi.fn();
    history.listen(listener);
    history.push("/a");
    listener.mockClear();

    // when
    window.dispatchEvent(new PopStateEvent("popstate", { state: null }));

    // then
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("defers a recorded morph pop by one task, never a microtask", async () => {
    // given
    const history = createMorphHistory();
    const listener = vi.fn();
    history.listen(listener);
    const origin: unknown = window.history.state;
    armMorph();
    history.push("/session");
    disarmMorph();
    listener.mockClear();

    // when
    window.history.replaceState(origin, "", "/");
    window.dispatchEvent(new PopStateEvent("popstate", { state: origin }));
    await Promise.resolve();

    // then
    expect(listener).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("keeps the record bounded", () => {
    // given
    const history = createMorphHistory();
    history.listen(() => undefined);

    // when
    for (let i = 0; i < 80; i += 1) {
      armMorph();
      history.push(`/p/${i}`);
      disarmMorph();
    }

    // then
    expect(JSON.parse(sessionStorage.getItem(STORE)!)).toHaveLength(50);
  });

  it("adds no types when morphs are not possible", () => {
    // given
    Reflect.deleteProperty(document, "startViewTransition");
    const history = createMorphHistory();
    const seen: string[] = [];
    history.listen(({ location }) => seen.push(location.key));
    armMorph();
    history.push("/x");
    disarmMorph();

    // then
    expect(replayTypes(seen[0]!, "default")).toEqual([]);
  });
});
