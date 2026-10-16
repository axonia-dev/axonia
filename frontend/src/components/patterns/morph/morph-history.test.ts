import { addTransitionType } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  armMorph,
  canMorph,
  createMorphHistory,
  disarmMorph,
  liveKey,
  MORPH,
  MORPH_BACK,
  replayTypes,
} from "@/components/patterns/morph/morph-history";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  addTransitionType: vi.fn(),
}));

const STORE = "axonia.morphs";

function allowMorphs(types = true) {
  Object.assign(document, { startViewTransition: vi.fn() });
  vi.stubGlobal("matchMedia", () => ({ matches: false }));
  vi.stubGlobal("CSS", { supports: () => types });
}

function morphPush(history: ReturnType<typeof createMorphHistory>, to: string) {
  armMorph();
  history.push(to);
  disarmMorph();
}

function popTo(state: unknown) {
  window.history.replaceState(state, "", "/");
  window.dispatchEvent(new PopStateEvent("popstate", { state }));
}

describe("createMorphHistory", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(addTransitionType).mockClear();
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
    morphPush(history, "/morphed");

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

  it("defers a recorded morph pop by one task, typed as a morph back", async () => {
    // given
    const history = createMorphHistory();
    const listener = vi.fn();
    history.listen(listener);
    const origin: unknown = window.history.state;
    morphPush(history, "/session");
    listener.mockClear();

    // when
    popTo(origin);
    await Promise.resolve();

    // then
    expect(listener).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(listener).toHaveBeenCalledTimes(1);
    expect(vi.mocked(addTransitionType).mock.calls).toEqual([
      [MORPH],
      [MORPH_BACK],
    ]);
  });

  it("drops a pending morph pop when a newer update arrives first", () => {
    // given
    const history = createMorphHistory();
    const listener = vi.fn();
    history.listen(listener);
    const origin: unknown = window.history.state;
    morphPush(history, "/session");
    listener.mockClear();

    // when
    popTo(origin);
    popTo({ usr: null, key: "elsewhere", idx: 0 });
    vi.runAllTimers();

    // then
    expect(listener).toHaveBeenCalledOnce();
    expect(listener.mock.calls[0]![0]).toMatchObject({
      action: "POP",
      location: { key: "elsewhere" },
    });
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("delivers nothing after unlisten", () => {
    // given
    const history = createMorphHistory();
    const listener = vi.fn();
    const unlisten = history.listen(listener);
    const origin: unknown = window.history.state;
    morphPush(history, "/session");
    listener.mockClear();
    popTo(origin);

    // when
    unlisten();
    vi.runAllTimers();

    // then
    expect(listener).not.toHaveBeenCalled();
  });

  it("follows a replaced entry, so back still reverses", () => {
    // given
    const history = createMorphHistory();
    const seen: string[] = [];
    history.listen(({ location }) => seen.push(location.key));
    const from = history.location.key;
    morphPush(history, "/session");

    // when
    history.replace("/session?tab=agenda");

    // then
    const [pushed, replaced] = [seen[0]!, seen[1]!];
    expect(replayTypes(replaced, from)).toEqual([MORPH, MORPH_BACK]);
    expect(replayTypes(pushed, from)).toEqual([]);
  });

  it("keeps the record bounded", () => {
    // given
    const history = createMorphHistory();
    history.listen(() => undefined);

    // when
    for (let i = 0; i < 80; i += 1) morphPush(history, `/p/${i}`);

    // then
    expect(JSON.parse(sessionStorage.getItem(STORE)!)).toHaveLength(50);
  });

  it("replays a recorded pair only where morphs are possible", () => {
    // given
    const history = createMorphHistory();
    const seen: string[] = [];
    history.listen(({ location }) => seen.push(location.key));
    const from = history.location.key;
    morphPush(history, "/x");

    // when
    Reflect.deleteProperty(document, "startViewTransition");
    const unsupported = replayTypes(from, seen[0]!);
    allowMorphs();
    const supported = replayTypes(from, seen[0]!);

    // then
    expect(unsupported).toEqual([]);
    expect(supported).toEqual([MORPH]);
  });
});

describe("canMorph", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, "startViewTransition");
  });

  it("needs view transitions, their types, and motion", () => {
    // given
    allowMorphs();

    // then
    expect(canMorph()).toBe(true);
  });

  it("refuses a browser without transition types", () => {
    // given
    allowMorphs(false);

    // then
    expect(canMorph()).toBe(false);
  });

  it("refuses an environment without CSS.supports", () => {
    // given
    allowMorphs();
    vi.stubGlobal("CSS", undefined);

    // then
    expect(canMorph()).toBe(false);
  });
});

describe("liveKey", () => {
  it("reads the key of the live history entry", () => {
    // when
    window.history.replaceState({ key: "k1", idx: 0 }, "");

    // then
    expect(liveKey()).toBe("k1");
  });

  it("falls back to the router's default key", () => {
    // when
    window.history.replaceState(null, "");

    // then
    expect(liveKey()).toBe("default");
  });
});
