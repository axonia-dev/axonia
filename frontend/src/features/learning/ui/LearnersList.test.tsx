import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLearners } from "@/features/learning/api/use-learning";
import { LearnersList } from "@/features/learning/ui/LearnersList";

vi.mock("@/features/learning/api/use-learning", () => ({
  useLearners: vi.fn(),
}));
function learners(state: {
  isLoading?: boolean;
  isError?: boolean;
  data?: unknown;
}) {
  vi.mocked(useLearners).mockReturnValue({
    isLoading: false,
    isError: false,
    data: undefined,
    ...state,
  } as never);
}

function renderList(tag = "kubernetes") {
  return render(
    <MemoryRouter>
      <LearnersList slug="platform" tag={tag} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("LearnersList", () => {
  it("heads the list with the area", () => {
    // given
    learners({
      data: [
        {
          userId: "mk",
          displayName: "Mara Keller",
          avatarUrl: null,
          declaredAt: "2025-11-14T09:00:00Z",
        },
      ],
    });

    // when
    renderList();

    // then
    expect(
      screen.getByRole("heading", { name: "Want to learn kubernetes" }),
    ).toBeInTheDocument();
    expect(vi.mocked(useLearners)).toHaveBeenCalledWith(
      "platform",
      "kubernetes",
    );
  });

  it("encodes the area in the bridge to who knows it", () => {
    // given
    learners({ data: [] });

    // when
    renderList("c++");

    // then
    expect(
      screen.getByRole("link", { name: "See who knows c++" }),
    ).toHaveAttribute("href", "/communities/platform/expertise?term=c%2B%2B");
  });

  it("says so while the learners load", () => {
    // given
    learners({ isLoading: true });

    // when
    renderList();

    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("says so when the learners cannot load", () => {
    // given
    learners({ isError: true });

    // when
    renderList();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the learners.",
    );
  });

  it("says so when nobody wants to learn the area", () => {
    // given
    learners({ data: [] });

    // when
    renderList();

    // then
    expect(
      screen.getByText("Nobody wants to learn this area."),
    ).toBeInTheDocument();
  });
});
