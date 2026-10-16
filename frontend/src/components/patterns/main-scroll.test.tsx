import { fireEvent, render, screen } from "@testing-library/react";
import { Link, MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { useMainScroll } from "@/components/patterns/main-scroll";

function Nav() {
  const navigate = useNavigate();
  return (
    <>
      <Link to="/a">a</Link>
      <Link to="/b">b</Link>
      <Link to="/a?page=2">a2</Link>
      <button type="button" onClick={() => void navigate(-1)}>
        back
      </button>
      <button
        type="button"
        onClick={() => void navigate("/a?view=month", { replace: true })}
      >
        month
      </button>
    </>
  );
}

function Page() {
  const { ref, onScroll } = useMainScroll();
  return <main ref={ref} onScroll={onScroll} data-testid="main" />;
}

function Harness({ remount }: Readonly<{ remount: boolean }>) {
  const { pathname } = useLocation();
  return (
    <>
      <Nav />
      {remount && pathname === "/b" ? <p>other</p> : <Page />}
    </>
  );
}

function setup(remount = false) {
  render(
    <MemoryRouter initialEntries={["/a"]}>
      <Harness remount={remount} />
    </MemoryRouter>,
  );
}

function scrollTo(top: number) {
  const main = screen.getByTestId("main");
  main.scrollTop = top;
  fireEvent.scroll(main);
}

describe("useMainScroll", () => {
  it("starts a new pathname at the top", () => {
    // given
    setup();
    scrollTo(400);

    // when
    fireEvent.click(screen.getByText("b"));

    // then
    expect(screen.getByTestId("main").scrollTop).toBe(0);
  });

  it("keeps the position on a search-only change", () => {
    // given
    setup();
    scrollTo(400);

    // when
    fireEvent.click(screen.getByText("a2"));

    // then
    expect(screen.getByTestId("main").scrollTop).toBe(400);
  });

  it("restores the entry's position on back", () => {
    // given
    setup();
    scrollTo(400);
    fireEvent.click(screen.getByText("b"));
    scrollTo(50);

    // when
    fireEvent.click(screen.getByText("back"));

    // then
    expect(screen.getByTestId("main").scrollTop).toBe(400);
  });

  it("restores across a remount of main", () => {
    // given
    setup(true);
    scrollTo(400);
    fireEvent.click(screen.getByText("b"));
    expect(screen.queryByTestId("main")).toBeNull();

    // when
    fireEvent.click(screen.getByText("back"));

    // then
    expect(screen.getByTestId("main").scrollTop).toBe(400);
  });

  it.each(["month", "a2"])(
    "keeps the position for back after %s changes only the search",
    (change) => {
      // given
      setup();
      scrollTo(400);
      fireEvent.click(screen.getByText(change));
      fireEvent.click(screen.getByText("b"));

      // when
      fireEvent.click(screen.getByText("back"));

      // then
      expect(screen.getByTestId("main").scrollTop).toBe(400);
    },
  );
});
