import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import type { Practitioner } from "@/features/expertise/domain/expertise";
import { PractitionerList } from "@/features/expertise/ui/PractitionerList";

const alice: Practitioner = {
  userId: "alice",
  displayName: "Alice Dupont",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER",
  evidence: {
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
};

const bob: Practitioner = {
  userId: "bob",
  displayName: "Bob Smith",
  avatarUrl: null,
  orgUnit: null,
  tier: "CONTRIBUTOR",
  evidence: {
    presentedCount: 0,
    contributedCount: 2,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
};

function renderList(
  props: Partial<Parameters<typeof PractitionerList>[0]> = {},
) {
  const defaults = {
    practitioners: [alice],
    isLoading: false,
    isError: false,
    demonstratedOnly: false,
    onShowAnyone: vi.fn(),
  };
  return render(
    <MemoryRouter>
      <PractitionerList {...defaults} {...props} />
    </MemoryRouter>,
  );
}

describe("PractitionerList", () => {
  it("renders the list of practitioners", () => {
    renderList({ practitioners: [alice, bob] });

    expect(screen.getByText("Alice Dupont")).toBeInTheDocument();
    expect(screen.getByText("Bob Smith")).toBeInTheDocument();
  });

  it("renders a list item for each practitioner", () => {
    renderList({ practitioners: [alice, bob] });

    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(2);
  });

  it("shows loading state when isLoading is true", () => {
    renderList({ practitioners: [], isLoading: true });

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows error message when isError is true", () => {
    renderList({ practitioners: [], isError: true });

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(
      screen.getByText(/could not load the people to ask/i),
    ).toBeInTheDocument();
  });

  it("suggests another term when nobody matches", () => {
    renderList({ practitioners: [] });

    expect(screen.getByText(/nobody matches/i)).toBeInTheDocument();
    expect(screen.getByText(/try another term/i)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Show anyone" }),
    ).not.toBeInTheDocument();
  });

  it("blames the filter and offers to clear it when it is on", async () => {
    const onShowAnyone = vi.fn();
    renderList({ practitioners: [], demonstratedOnly: true, onShowAnyone });

    expect(screen.getByText(/nobody has done this here/i)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Show anyone" }));

    expect(onShowAnyone).toHaveBeenCalledTimes(1);
  });

  it("renders a single practitioner correctly", () => {
    renderList({ practitioners: [alice] });

    expect(screen.getByText("Alice Dupont")).toBeInTheDocument();
    expect(screen.queryByText("Bob Smith")).not.toBeInTheDocument();
  });
});
