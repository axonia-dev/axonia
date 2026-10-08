import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLearners } from "@/features/learning/api/use-learning";
import {
  learners,
  page,
  quietHooks,
  renderPage,
} from "@/test/learning-page-view";

// Split out of LearningPage.test.tsx (300-line ceiling): what the community wants to learn.

vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/features/learning/api/use-learning", () => ({
  useLearningPage: vi.fn(),
  useLearners: vi.fn(),
  useAddIntent: vi.fn(),
  useRemoveIntent: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  quietHooks();
});

describe("LearningPage wanted areas", () => {
  it("reads zero supply as none and sorts as the server sent it", () => {
    // given
    page({
      wanted: [
        { tag: "observability", learners: 7, canBeAsked: 0 },
        { tag: "kafka", learners: 4, canBeAsked: 3 },
      ],
    });

    // when
    renderPage();

    // then
    const rows = screen.getAllByRole("row").slice(1);
    expect(within(rows[0]!).getByText("observability")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("none")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("kafka")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("3")).toBeInTheDocument();
  });

  it("picks the area from the URL and lists its learners with a bridge to who knows it", () => {
    // given
    page({ wanted: [{ tag: "kubernetes", learners: 1, canBeAsked: 1 }] });
    learners([
      {
        userId: "mk",
        displayName: "Mara Keller",
        avatarUrl: null,
        declaredAt: "2025-11-14T09:00:00Z",
      },
    ]);

    // when
    renderPage("?area=kubernetes");

    // then
    expect(vi.mocked(useLearners)).toHaveBeenCalledWith(
      "platform",
      "kubernetes",
    );
    expect(screen.getByText("Mara Keller")).toBeInTheDocument();
    expect(screen.getByText("Since 14 Nov 2025")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "See who knows kubernetes" }),
    ).toHaveAttribute(
      "href",
      "/communities/platform/expertise?term=kubernetes",
    );
  });

  it("reads a hand-typed area from the URL in its normal form", () => {
    // given
    page({ wanted: [{ tag: "kubernetes", learners: 1, canBeAsked: 1 }] });

    // when
    renderPage("?area=Kubernetes");

    // then
    expect(vi.mocked(useLearners)).toHaveBeenCalledWith(
      "platform",
      "kubernetes",
    );
    expect(
      screen.getByRole("heading", { name: "Want to learn kubernetes" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "kubernetes, 1 wants to learn, 1 can be asked",
      }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByRole("link", { name: "See who knows kubernetes" }),
    ).toHaveAttribute(
      "href",
      "/communities/platform/expertise?term=kubernetes",
    );
  });

  it.each(["?area=", "?area=%21%21"])(
    "takes a blank area in the URL (%s) as no pick",
    (search) => {
      // given
      page({ wanted: [{ tag: "kafka", learners: 1, canBeAsked: 0 }] });

      // when
      renderPage(search);

      // then
      expect(vi.mocked(useLearners)).not.toHaveBeenCalled();
      expect(
        screen.queryByRole("link", { name: /see who knows/i }),
      ).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /^kafka,/ })).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    },
  );

  it("picking a row replaces the area in the URL", async () => {
    // given
    page({ wanted: [{ tag: "kafka", learners: 1, canBeAsked: 0 }] });
    renderPage();

    // when
    await userEvent.click(
      screen.getByRole("button", {
        name: "kafka, 1 wants to learn, none can be asked",
      }),
    );

    // then
    expect(screen.getByTestId("location")).toHaveTextContent("?area=kafka");
    expect(screen.getByTestId("location")).toHaveAttribute(
      "data-navigation",
      "REPLACE",
    );
  });

  it("says so when nothing is wanted yet", () => {
    // given
    page({ wanted: [] });

    // when
    renderPage();

    // then
    expect(screen.getByText("No areas wanted yet.")).toBeInTheDocument();
  });
});
