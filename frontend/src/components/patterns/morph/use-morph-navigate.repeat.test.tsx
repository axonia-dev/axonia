import { act, render, screen } from "@testing-library/react";
import { addTransitionType } from "react";
import {
  Route,
  Routes,
  unstable_HistoryRouter as HistoryRouter,
  useLocation,
} from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createMorphHistory } from "@/components/patterns/morph/morph-history";
import { useMorphNavigate } from "@/components/patterns/morph/use-morph-navigate";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  addTransitionType: vi.fn(),
}));

type MorphTo = ReturnType<typeof useMorphNavigate>;

function Source({ onRender }: Readonly<{ onRender: (to: MorphTo) => void }>) {
  onRender(useMorphNavigate());
  return null;
}

function Where() {
  return <output data-testid="where">{useLocation().pathname}</output>;
}

function renderSource() {
  const handlers: MorphTo[] = [];
  const history = createMorphHistory();
  history.push("/a");
  render(
    <HistoryRouter history={history}>
      <Where />
      <Routes>
        <Route
          path="/a"
          element={<Source onRender={(to) => handlers.push(to)} />}
        />
        <Route path="*" element={null} />
      </Routes>
    </HistoryRouter>,
  );
  return { history, handlers };
}

const ready = () => Promise.resolve();

describe("useMorphNavigate on a page the morph already left", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(addTransitionType).mockClear();
    Object.assign(document, { startViewTransition: vi.fn() });
    vi.stubGlobal("matchMedia", () => ({ matches: false }));
    vi.stubGlobal("CSS", { supports: () => true });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, "startViewTransition");
  });

  it("pushes once for a second click on the old page after a warm morph", async () => {
    // given
    const { handlers } = renderSource();
    const oldPage = handlers.at(-1);
    const entries = window.history.length;

    // when
    await act(() => oldPage?.("/b", { ready }));
    await act(() => oldPage?.("/b", { ready }));

    // then
    expect(screen.getByTestId("where")).toHaveTextContent("/b");
    expect(window.history).toHaveLength(entries + 1);
    expect(addTransitionType).toHaveBeenCalledOnce();
  });

  it("morphs again from the page once back has returned to it", async () => {
    // given
    const { history, handlers } = renderSource();
    await act(() => handlers.at(-1)?.("/b", { ready }));
    await act(async () => {
      history.go(-1);
      await new Promise((resolve) => {
        globalThis.addEventListener("popstate", resolve, { once: true });
      });
      await new Promise((resolve) => {
        setTimeout(resolve, 0);
      });
    });
    vi.mocked(addTransitionType).mockClear();

    // when
    await act(() => handlers.at(-1)?.("/b", { ready }));

    // then
    expect(screen.getByTestId("where")).toHaveTextContent("/b");
    expect(addTransitionType).toHaveBeenCalledWith("morph");
  });
});
