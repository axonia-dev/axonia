import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { pageOf } from "@/features/mentoring/test-data";
import { GuidedStart, PEOPLE_ID } from "@/features/mentoring/ui/GuidedStart";

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

const onSwitchedOn = vi.fn();

function start(overrides: Partial<MentoringPage> = {}) {
  const mutate = vi.fn();
  vi.mocked(useSetAvailability).mockReturnValue({
    mutate,
    isPending: false,
  } as never);
  render(
    <MemoryRouter>
      <GuidedStart
        slug="platform"
        page={pageOf({ mine: OFF, guidedStart: true, ...overrides })}
        onSwitchedOn={onSwitchedOn}
      />
    </MemoryRouter>,
  );
  return mutate;
}

function step(name: string) {
  return screen.getByText(name, { selector: "p" }).closest("li")!;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("GuidedStart", () => {
  it("links the areas step to Learning and My expertise", () => {
    // when
    start({ canMentorOn: [], wantsToGrowIn: [] });

    // then
    expect(
      screen.getByRole("link", { name: "Areas to grow in, in Learning" }),
    ).toHaveAttribute("href", "/communities/platform/learning");
    expect(
      screen.getByRole("link", { name: "Areas to mentor on, in My expertise" }),
    ).toHaveAttribute("href", "/communities/platform/me/expertise");
    expect(step("Areas")).toHaveAttribute("aria-current", "step");
  });

  it("ticks the areas step once either list has an area", () => {
    // when
    start({ canMentorOn: [], wantsToGrowIn: ["kafka"] });

    // then
    expect(within(step("Areas")).getByLabelText("Done")).toBeInTheDocument();
    expect(within(step("Areas")).getByText("kafka")).toBeInTheDocument();
    expect(step("Switch on")).toHaveAttribute("aria-current", "step");
  });

  it("switches on from the second step", async () => {
    // given
    const mutate = start();

    // when
    await userEvent.click(
      screen.getByRole("switch", { name: "Looking for a mentor" }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith(
      { ...OFF, lookingForMentor: true },
      expect.anything(),
    );
    expect(onSwitchedOn).toHaveBeenCalledWith("mentee");
  });

  it("leads the third step to the lists", async () => {
    // given
    start();
    const lists = document.createElement("div");
    lists.id = PEOPLE_ID;
    lists.scrollIntoView = vi.fn();
    document.body.append(lists);

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "See who is here" }),
    );

    // then
    expect(lists.scrollIntoView).toHaveBeenCalled();
    lists.remove();
  });
});
