import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { MorphLink } from "@/components/patterns/morph/MorphLink";

function Where() {
  return <output>{useLocation().pathname}</output>;
}

function renderLink(onIntent?: () => void) {
  render(
    <MemoryRouter initialEntries={["/a"]}>
      <Where />
      <MorphLink to="/b" {...(onIntent !== undefined && { onIntent })}>
        open
      </MorphLink>
    </MemoryRouter>,
  );
  return screen.getByRole("link", { name: "open" });
}

describe("MorphLink", () => {
  it("points at its destination", () => {
    // when
    const link = renderLink();

    // then
    expect(link).toHaveAttribute("href", "/b");
  });

  it("takes over a plain click and navigates", async () => {
    // given
    const link = renderLink();

    // when
    const notPrevented = fireEvent.click(link, { button: 0 });

    // then
    expect(notPrevented).toBe(false);
    expect(await screen.findByText("/b")).toBeInTheDocument();
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
    let prevented: boolean | undefined;
    const settle = (event: Event) => {
      prevented = event.defaultPrevented;
      event.preventDefault();
    };
    globalThis.addEventListener("click", settle, { once: true });

    // when
    fireEvent.click(link, modifier);

    // then
    expect(prevented).toBe(false);
    expect(screen.getByText("/a")).toBeInTheDocument();
  });

  it("signals intent on hover and on focus", () => {
    // given
    const onIntent = vi.fn();
    const link = renderLink(onIntent);

    // when
    fireEvent.pointerEnter(link);
    fireEvent.focus(link);

    // then
    expect(onIntent).toHaveBeenCalledTimes(2);
  });
});
