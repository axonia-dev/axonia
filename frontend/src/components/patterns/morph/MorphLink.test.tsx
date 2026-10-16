import { fireEvent, render, screen } from "@testing-library/react";
import { addTransitionType } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { MorphLink } from "@/components/patterns/morph/MorphLink";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  addTransitionType: vi.fn(),
}));

type Props = Omit<Parameters<typeof MorphLink>[0], "to" | "children">;

function Where() {
  return <output>{useLocation().pathname}</output>;
}

function renderLink(props: Props = {}) {
  render(
    <MemoryRouter initialEntries={["/a"]}>
      <Where />
      <MorphLink to="/b" {...props}>
        open
      </MorphLink>
    </MemoryRouter>,
  );
  return screen.getByRole("link", { name: "open" });
}

function browserVerdict() {
  let prevented: boolean | undefined;
  const settle = (event: Event) => {
    prevented = event.defaultPrevented;
    event.preventDefault();
  };
  globalThis.addEventListener("click", settle, { once: true });
  return () => prevented;
}

describe("MorphLink", () => {
  beforeEach(() => {
    vi.mocked(addTransitionType).mockClear();
    Object.assign(document, { startViewTransition: vi.fn() });
    vi.stubGlobal("matchMedia", () => ({ matches: false }));
    vi.stubGlobal("CSS", { supports: () => true });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, "startViewTransition");
  });

  it("points at its destination", () => {
    // when
    const link = renderLink();

    // then
    expect(link).toHaveAttribute("href", "/b");
  });

  it("takes over a plain click and navigates as a morph", async () => {
    // given
    const link = renderLink();

    // when
    const notPrevented = fireEvent.click(link, { button: 0 });

    // then
    expect(notPrevented).toBe(false);
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).toHaveBeenCalledWith("morph");
  });

  it.each([
    { metaKey: true },
    { ctrlKey: true },
    { shiftKey: true },
    { altKey: true },
    { button: 1 },
  ])("leaves a modified click %o to the browser", (modifier) => {
    // given
    const link = renderLink();
    const prevented = browserVerdict();

    // when
    fireEvent.click(link, modifier);

    // then
    expect(prevented()).toBe(false);
    expect(screen.getByText("/a")).toBeInTheDocument();
  });

  it.each([{ target: "_blank" }, { reloadDocument: true }])(
    "leaves %o to the browser",
    (props) => {
      // given
      const link = renderLink(props);
      const prevented = browserVerdict();

      // when
      fireEvent.click(link);

      // then
      expect(prevented()).toBe(false);
      expect(addTransitionType).not.toHaveBeenCalled();
    },
  );

  it("runs the caller's click first, and morphs after it", async () => {
    // given
    const onClick = vi.fn();
    const link = renderLink({ onClick });

    // when
    fireEvent.click(link);

    // then
    expect(onClick).toHaveBeenCalledOnce();
    expect(await screen.findByText("/b")).toBeInTheDocument();
    expect(addTransitionType).toHaveBeenCalledWith("morph");
  });

  it("stays put when the caller's click prevents it", () => {
    // given
    const link = renderLink({ onClick: (event) => event.preventDefault() });

    // when
    fireEvent.click(link);

    // then
    expect(screen.getByText("/a")).toBeInTheDocument();
    expect(addTransitionType).not.toHaveBeenCalled();
  });

  it("signals intent on hover and on focus, beside the caller's handlers", () => {
    // given
    const onIntent = vi.fn();
    const onPointerEnter = vi.fn();
    const onFocus = vi.fn();
    const link = renderLink({ onIntent, onPointerEnter, onFocus });

    // when
    fireEvent.pointerEnter(link);
    fireEvent.focus(link);

    // then
    expect(onIntent).toHaveBeenCalledTimes(2);
    expect(onPointerEnter).toHaveBeenCalledOnce();
    expect(onFocus).toHaveBeenCalledOnce();
  });
});
