import { act, fireEvent, render, screen } from "@testing-library/react";
import { addTransitionType } from "react";
import { Link, MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  liftCurtain,
  raiseCurtain,
} from "@/components/patterns/ceremony/curtain";
import {
  type MorphOptions,
  useMorphNavigate,
} from "@/components/patterns/morph/use-morph-navigate";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  addTransitionType: vi.fn(),
}));

function Harness({ options }: Readonly<{ options: MorphOptions }>) {
  const morphTo = useMorphNavigate();
  const { pathname } = useLocation();
  return (
    <>
      <output>{pathname}</output>
      <button type="button" onClick={() => void morphTo("/b", options)}>
        morph
      </button>
      <Link to="/c">elsewhere</Link>
    </>
  );
}

function renderHarness(options: MorphOptions = {}) {
  render(
    <MemoryRouter initialEntries={["/a"]}>
      <Harness options={options} />
    </MemoryRouter>,
  );
}

function allowMorphs(reduced = false) {
  Object.assign(document, { startViewTransition: vi.fn() });
  vi.stubGlobal("matchMedia", () => ({ matches: reduced }));
}

const never = () => new Promise<never>(() => undefined);

describe("useMorphNavigate", () => {
  beforeEach(() => {
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
    renderHarness({ ready });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(ready).toHaveBeenCalledOnce();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly, without waiting, under reduced motion", async () => {
    // given
    allowMorphs(true);
    const ready = vi.fn(never);
    renderHarness({ ready });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(ready).toHaveBeenCalledOnce();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly when the destination fails to get ready", async () => {
    // given
    allowMorphs();
    renderHarness({ ready: () => Promise.reject(new Error("offline")) });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));

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
    renderHarness({ ready: late });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));
    await act(() => vi.advanceTimersByTimeAsync(300));

    // then
    expect(screen.getByText("/b")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("navigates plainly while a ceremony curtain is up", async () => {
    // given
    allowMorphs();
    raiseCurtain();
    renderHarness({ ready: () => Promise.resolve() });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("marks the navigation as a morph otherwise", async () => {
    // given
    allowMorphs();
    renderHarness({ ready: () => Promise.resolve() });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));

    // then
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).toHaveBeenCalledWith("morph");
  });

  it("abandons the morph when the reader moved on meanwhile", async () => {
    // given
    allowMorphs();
    let settle = (): void => undefined;
    const slow = () =>
      new Promise<void>((resolve) => {
        settle = resolve;
      });
    renderHarness({ ready: slow });

    // when
    fireEvent.click(screen.getByRole("button", { name: "morph" }));
    fireEvent.click(screen.getByRole("link", { name: "elsewhere" }));
    await screen.findByText("/c");
    await act(async () => {
      settle();
      await new Promise((resolve) => {
        setTimeout(resolve, 0);
      });
    });

    // then
    expect(screen.getByText("/c")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });
});
