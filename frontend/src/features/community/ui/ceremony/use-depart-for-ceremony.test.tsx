import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import {
  Link,
  MemoryRouter,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { raiseCurtain } from "@/components/patterns/ceremony/curtain";
import {
  handOffCeremony,
  pendingCeremony,
} from "@/features/community/api/ceremony-handoff";
import { departForCeremony } from "@/features/community/ui/ceremony/ceremony-arrival";
import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";

vi.mock("@/features/community/ui/ceremony/ceremony-arrival", () => ({
  departForCeremony: vi.fn(),
}));

const mockDepart = vi.mocked(departForCeremony);

let user: ReturnType<typeof userEvent.setup>;
let dark: () => void;
const departed: { fn?: ReturnType<typeof useDepartForCeremony> } = {};

function Departing() {
  const depart = useDepartForCeremony();
  useEffect(() => {
    departed.fn = depart;
  }, [depart]);
  return (
    <button
      type="button"
      onClick={() => void depart("welcome", "backend-guild")}
    >
      join
    </button>
  );
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function renderDeparting({ unmountOnLeave }: { unmountOnLeave: boolean }) {
  return render(
    <MemoryRouter initialEntries={["/communities"]}>
      {!unmountOnLeave && <Departing />}
      <Link to="/elsewhere">elsewhere</Link>
      <Routes>
        <Route
          path="/communities"
          element={unmountOnLeave ? <Departing /> : null}
        />
        <Route path="*" element={null} />
      </Routes>
      <Where />
    </MemoryRouter>,
  );
}

function curtain(): Element | null {
  return document.querySelector("[data-ceremony-curtain]");
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  mockDepart.mockReset();
  mockDepart.mockImplementation(
    (kind, slug) =>
      new Promise<void>((resolve) => {
        handOffCeremony(kind, slug);
        raiseCurtain();
        dark = resolve;
      }),
  );
});

afterEach(() => {
  curtain()?.remove();
  sessionStorage.clear();
});

describe("useDepartForCeremony", () => {
  it("enters the community once the page is dark", async () => {
    // given
    renderDeparting({ unmountOnLeave: false });
    await user.click(screen.getByRole("button", { name: "join" }));

    // when
    await act(() => Promise.resolve().then(dark));

    // then
    expect(screen.getByTestId("where")).toHaveTextContent(
      "/communities/backend-guild",
    );
    expect(pendingCeremony("backend-guild")).toBe("welcome");
  });

  it("lets a navigation taken during the fade win, and drops the ceremony", async () => {
    // given
    renderDeparting({ unmountOnLeave: false });
    await user.click(screen.getByRole("button", { name: "join" }));
    await user.click(screen.getByRole("link", { name: "elsewhere" }));

    // when
    await act(() => Promise.resolve().then(dark));

    // then
    expect(screen.getByTestId("where")).toHaveTextContent("/elsewhere");
    expect(curtain()).toBeNull();
    expect(pendingCeremony("backend-guild")).toBeNull();
  });

  it("stays put when the departing page went away during the fade", async () => {
    // given
    renderDeparting({ unmountOnLeave: true });
    await user.click(screen.getByRole("button", { name: "join" }));
    await user.click(screen.getByRole("link", { name: "elsewhere" }));

    // when
    await act(() => Promise.resolve().then(dark));

    // then
    expect(screen.getByTestId("where")).toHaveTextContent("/elsewhere");
    expect(pendingCeremony("backend-guild")).toBeNull();
  });

  it("raises no curtain when the page went away before the departure began", async () => {
    // given
    renderDeparting({ unmountOnLeave: true });
    await user.click(screen.getByRole("link", { name: "elsewhere" }));

    // when
    await act(() => departed.fn?.("welcome", "backend-guild"));

    // then
    expect(mockDepart).not.toHaveBeenCalled();
    expect(curtain()).toBeNull();
    expect(pendingCeremony("backend-guild")).toBeNull();
    expect(screen.getByTestId("where")).toHaveTextContent("/elsewhere");
  });
});
