import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import { pairingOf } from "@/features/mentoring/test-data";
import { StripTrail } from "@/features/mentoring/ui/my-mentoring/StripTrail";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));

beforeEach(() => {
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
});

describe("StripTrail", () => {
  it("names how far the pair has come and the topic in progress", () => {
    // given
    const pairing = pairingOf({
      stops: ["DONE", "DONE", "DONE", "IN_PROGRESS", "TODO", "TODO", "TODO"],
      nowTopic: "Giving feedback",
    });

    // when
    const { container } = render(<StripTrail pairing={pairing} />);

    // then
    expect(
      screen.getByRole("img", {
        name: "3 of 7 topics reached, now Giving feedback",
      }),
    ).toBeInTheDocument();
    expect(container.querySelectorAll("[data-state]")).toHaveLength(7);
    expect(screen.getByTestId("strip-walked")).toBeInTheDocument();
  });

  it("an empty journal draws a dotted road with the pair at the trailhead", () => {
    // given
    const pairing = pairingOf({ stops: [], nowTopic: null });

    // when
    const { container } = render(<StripTrail pairing={pairing} />);

    // then
    const strip = screen.getByRole("img", { name: "No topics yet" });
    expect(strip.querySelector("path")).toHaveAttribute(
      "stroke-dasharray",
      "1 30",
    );
    expect(container.querySelector("[data-state]")).toBeNull();
    expect(screen.queryByTestId("strip-walked")).not.toBeInTheDocument();
    expect(container.querySelector("span[style]")).toHaveStyle({
      left: "2%",
    });
  });

  it("walks nowhere while every stop is still to do", () => {
    // given
    const pairing = pairingOf({ stops: ["TODO", "TODO"] });

    // when
    render(<StripTrail pairing={pairing} />);

    // then
    expect(
      screen.getByRole("img", { name: "0 of 2 topics reached" }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("strip-walked")).not.toBeInTheDocument();
  });
});
