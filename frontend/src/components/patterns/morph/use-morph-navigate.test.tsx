import { act, fireEvent, render, screen } from "@testing-library/react";
import { addTransitionType } from "react";
import {
  Link,
  Route,
  Routes,
  unstable_HistoryRouter as HistoryRouter,
  useLocation,
} from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  liftCurtain,
  raiseCurtain,
} from "@/components/patterns/ceremony/curtain";
import {
  createMorphHistory,
  MORPH,
  MORPH_BACK,
  replayTypes,
} from "@/components/patterns/morph/morph-history";
import {
  type MorphOptions,
  useMorphNavigate,
} from "@/components/patterns/morph/use-morph-navigate";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  addTransitionType: vi.fn(),
}));

function Source({
  options,
  other = {},
}: Readonly<{ options: MorphOptions; other?: MorphOptions }>) {
  const morphTo = useMorphNavigate();
  return (
    <>
      <button type="button" onClick={() => void morphTo("/b", options)}>
        morph
      </button>
      <button type="button" onClick={() => void morphTo("/d", other)}>
        other
      </button>
      <Link to="/c">elsewhere</Link>
    </>
  );
}

function Where() {
  const location = useLocation();
  const state: unknown = location.state;
  return (
    <output data-state={JSON.stringify(state)} data-testid="where">
      {location.pathname}
    </output>
  );
}

function renderSource(options: MorphOptions = {}, other: MorphOptions = {}) {
  const history = createMorphHistory();
  history.push("/a");
  render(
    <HistoryRouter history={history}>
      <Where />
      <Routes>
        <Route path="/a" element={<Source options={options} other={other} />} />
        <Route path="*" element={null} />
      </Routes>
    </HistoryRouter>,
  );
  return history;
}

const where = () => screen.getByTestId("where");
const click = () =>
  fireEvent.click(screen.getByRole("button", { name: "morph" }));

function allowMorphs(reduced = false) {
  Object.assign(document, { startViewTransition: vi.fn() });
  vi.stubGlobal("matchMedia", () => ({ matches: reduced }));
  vi.stubGlobal("CSS", { supports: () => true });
}

const never = () => new Promise<never>(() => undefined);

function deferred() {
  let settle = (): void => undefined;
  const promise = new Promise<void>((resolve) => {
    settle = resolve;
  });
  return { ready: () => promise, settle: () => settle() };
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

describe("useMorphNavigate", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(addTransitionType).mockClear();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, "startViewTransition");
    liftCurtain();
    document
      .querySelectorAll("[data-ceremony-curtain]")
      .forEach((curtain) => curtain.remove());
  });

  it("navigates plainly, without waiting, where morphs are impossible", async () => {
    // given
    const ready = vi.fn(never);
    renderSource({ ready });

    // when
    click();

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(ready).toHaveBeenCalledOnce();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly, without waiting, under reduced motion", async () => {
    // given
    allowMorphs(true);
    const ready = vi.fn(never);
    renderSource({ ready });

    // when
    click();

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(ready).toHaveBeenCalledOnce();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly when the destination fails to get ready", async () => {
    // given
    allowMorphs();
    renderSource({ ready: () => Promise.reject(new Error("offline")) });

    // when
    click();

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly when the destination is late", async () => {
    // given
    vi.useFakeTimers();
    allowMorphs();
    const late = () =>
      new Promise<void>((resolve) => {
        setTimeout(resolve, 1_000);
      });
    renderSource({ ready: late });

    // when
    click();
    await act(() => vi.advanceTimersByTimeAsync(300));

    // then
    expect(where()).toHaveTextContent("/b");
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly while a ceremony curtain is up", async () => {
    // given
    allowMorphs();
    raiseCurtain();
    renderSource({ ready: () => Promise.resolve() });

    // when
    click();

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("marks the push as a morph, records it, and replays it on back", async () => {
    // given
    allowMorphs();
    const history = renderSource({ ready: () => Promise.resolve() });
    const from = history.location.key;

    // when
    click();
    await screen.findByText("/b");
    const to = history.location.key;
    vi.mocked(addTransitionType).mockClear();
    act(() => history.go(-1));
    await act(async () => {
      await new Promise((resolve) => {
        globalThis.addEventListener("popstate", resolve, { once: true });
      });
    });
    await flush();

    // then
    expect(replayTypes(from, to)).toEqual([MORPH]);
    expect(where()).toHaveTextContent("/a");
    expect(vi.mocked(addTransitionType).mock.calls).toEqual([
      [MORPH],
      [MORPH_BACK],
    ]);
  });

  it("forwards replace and state to the navigation", async () => {
    // given
    allowMorphs();
    const history = renderSource({ replace: true, state: { from: "list" } });
    const before = history.location.key;

    // when
    click();
    await screen.findByText("/b");

    // then
    expect(history.action).toBe("REPLACE");
    expect(history.location.key).not.toBe(before);
    expect(where()).toHaveAttribute("data-state", '{"from":"list"}');
  });

  it("abandons the morph when the reader moved on and the source is gone", async () => {
    // given
    allowMorphs();
    const slow = deferred();
    renderSource({ ready: slow.ready });

    // when
    click();
    fireEvent.click(screen.getByRole("link", { name: "elsewhere" }));
    await screen.findByText("/c");
    expect(screen.queryByRole("button", { name: "morph" })).toBeNull();
    slow.settle();
    await flush();

    // then
    expect(where()).toHaveTextContent("/c");
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("pushes once for a double click", async () => {
    // given
    allowMorphs();
    const slow = deferred();
    renderSource({ ready: slow.ready });
    const entries = window.history.length;

    // when
    click();
    click();
    slow.settle();
    await flush();

    // then
    expect(where()).toHaveTextContent("/b");
    expect(window.history).toHaveLength(entries + 1);
    expect(addTransitionType).toHaveBeenCalledOnce();
  });

  it("lets only the latest pending call navigate", async () => {
    // given
    allowMorphs();
    const first = deferred();
    const second = deferred();
    renderSource({ ready: first.ready }, { ready: second.ready });

    // when
    click();
    fireEvent.click(screen.getByRole("button", { name: "other" }));
    first.settle();
    await flush();
    second.settle();
    await flush();

    // then
    expect(where()).toHaveTextContent("/d");
    expect(addTransitionType).toHaveBeenCalledOnce();
  });
});
