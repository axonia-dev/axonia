import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notify } from "@/components/ui/toast";
import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { pageOf } from "@/features/mentoring/test-data";
import { AvailabilityDoors } from "@/features/mentoring/ui/AvailabilityDoors";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useSetAvailability: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

const OFF = {
  takingMentees: false,
  menteeLimit: null,
  lookingForMentor: false,
};

function saving() {
  const mutate = vi.fn((_next: unknown, options?: { onSuccess?: () => void }) =>
    options?.onSuccess?.(),
  );
  vi.mocked(useSetAvailability).mockReturnValue({
    mutate,
    isPending: false,
  } as never);
  return mutate;
}

function doors(
  overrides: Partial<MentoringPage> = {},
  focusDoor?: "mentor" | "mentee",
) {
  render(
    <MemoryRouter>
      <AvailabilityDoors
        slug="platform"
        page={pageOf(overrides)}
        {...(focusDoor === undefined ? {} : { focusDoor })}
      />
    </MemoryRouter>,
  );
}

function taking(menteeLimit: number | null) {
  return { ...OFF, takingMentees: true, menteeLimit };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("AvailabilityDoors", () => {
  it("only the switch toggles a door", async () => {
    // given
    const mutate = saving();
    doors({ mine: OFF });

    // when
    await userEvent.click(
      screen.getByRole("heading", { name: "Looking for a mentor" }),
    );
    await userEvent.click(screen.getByText("Wants to grow in"));
    await userEvent.click(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    );

    // then
    expect(mutate).toHaveBeenCalledOnce();
    expect(mutate).toHaveBeenCalledWith(
      { ...OFF, lookingForMentor: true },
      expect.anything(),
    );
  });

  it("an open door wears the colour of the person it lists, the same as in the journal", () => {
    // given
    saving();

    // when
    doors({ mine: { ...taking(null), lookingForMentor: true } });

    // then
    const mentor = screen.getByRole("region", { name: "Taking mentees" });
    const mentee = screen.getByRole("region", { name: "Looking for a mentor" });
    expect(mentor.className).toContain("var(--mentor)");
    expect(mentee.className).toContain("var(--mentee)");
  });

  it("saves the limit on each step with a toast and has no Save button", async () => {
    // given
    const mutate = saving();
    doors({ mine: taking(3) });

    // when
    await userEvent.click(screen.getByRole("button", { name: "More mentees" }));

    // then
    expect(mutate).toHaveBeenCalledWith(taking(4), expect.anything());
    expect(notify).toHaveBeenCalledWith("Saved");
    expect(
      screen.queryByRole("button", { name: /save/i }),
    ).not.toBeInTheDocument();
  });

  it("steps from 10 to No limit", async () => {
    // given
    const mutate = saving();
    doors({ mine: taking(10) });

    // when
    await userEvent.click(screen.getByRole("button", { name: "More mentees" }));

    // then
    expect(mutate).toHaveBeenCalledWith(taking(null), expect.anything());
  });

  it("steps back from No limit to 10", async () => {
    // given
    const mutate = saving();
    doors({ mine: taking(null) });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Fewer mentees" }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith(taking(10), expect.anything());
    expect(screen.getByText("No limit")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "More mentees" })).toBeDisabled();
  });

  it("hands focus to the other step when one reaches its bound", async () => {
    // given
    saving();
    doors({ mine: taking(10) });

    // when
    await userEvent.click(screen.getByRole("button", { name: "More mentees" }));

    // then
    expect(screen.getByRole("button", { name: "Fewer mentees" })).toHaveFocus();
  });

  it("keeps every control usable while a save is in flight", () => {
    // given
    vi.mocked(useSetAvailability).mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as never);

    // when
    doors({ mine: taking(3) });

    // then
    expect(
      screen.getByRole("switch", { name: "Taking mentees" }),
    ).toBeEnabled();
    expect(screen.getByRole("button", { name: "More mentees" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Fewer mentees" })).toBeEnabled();
  });

  it("puts focus on the door a guided start gave way to", () => {
    // given
    saving();

    // when
    doors({ mine: { ...OFF, lookingForMentor: true } }, "mentee");

    // then
    expect(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    ).toHaveFocus();
  });

  it("goes no lower than one mentee", () => {
    // given
    saving();

    // when
    doors({ mine: taking(1) });

    // then
    expect(
      screen.getByRole("button", { name: "Fewer mentees" }),
    ).toBeDisabled();
  });

  it("offers no stepper while mentees are not taken", () => {
    // given
    saving();

    // when
    doors({ mine: OFF });

    // then
    expect(
      screen.queryByRole("group", { name: "Most mentees at once" }),
    ).not.toBeInTheDocument();
  });

  it("nudges to My expertise when no area is behind the door", () => {
    // given
    saving();

    // when
    doors({ mine: taking(null), canMentorOn: [], wantsToGrowIn: [] });

    // then
    expect(screen.getByText("No area to mentor on yet.")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Add areas in My expertise" }),
    ).toHaveAttribute("href", "/communities/platform/me/expertise");
    expect(
      screen.getByRole("link", { name: "Add areas in Learning" }),
    ).toHaveAttribute("href", "/communities/platform/learning");
  });

  it("shows the areas behind each door", () => {
    // given
    saving();

    // when
    doors({ canMentorOn: ["kafka"], wantsToGrowIn: ["testing"] });

    // then
    expect(screen.getByText("kafka")).toBeInTheDocument();
    expect(screen.getByText("testing")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Add in My expertise" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("No area to grow in yet."),
    ).not.toBeInTheDocument();
  });

  it("locks both doors in an archived community", () => {
    // given
    saving();

    // when
    doors({ archived: true });

    // then
    expect(
      screen.getByRole("switch", { name: "Taking mentees" }),
    ).toBeDisabled();
    expect(screen.getByText("This community is archived.")).toBeInTheDocument();
  });
});
