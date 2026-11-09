import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { HealthStatus } from "@/features/health/domain/status";
import { StatusPill } from "@/features/health/ui/StatusPill";

describe("StatusPill", () => {
  it.each<[HealthStatus, string]>([
    [{ code: "ONE_VOICE", level: "WORRY" }, "One voice"],
    [{ code: "SLOWING", level: "WATCH" }, "Slowing"],
    [{ code: "ON_RHYTHM", level: "GOOD" }, "On rhythm"],
    [{ code: "SHARED_STAGE", level: "GREAT" }, "Shared stage"],
    [{ code: "NOTHING_OPENED", level: "QUIET" }, "Nothing opened yet"],
  ])("carries the word and the level of %j", (status, word) => {
    // when
    render(<StatusPill status={status} />);

    // then
    expect(screen.getByText(word)).toHaveAttribute("data-level", status.level);
  });

  it("can carry another word in the status's colour", () => {
    // when
    render(
      <StatusPill
        status={{ code: "ON_RHYTHM", level: "GOOD" }}
        label="About monthly"
      />,
    );

    // then
    expect(screen.getByText("About monthly")).toHaveAttribute(
      "data-level",
      "GOOD",
    );
    expect(screen.queryByText("On rhythm")).not.toBeInTheDocument();
  });

  it("never judges in colour alone: the dot is decoration beside the word", () => {
    // when
    const { container } = render(
      <StatusPill status={{ code: "DORMANT", level: "WORRY" }} />,
    );

    // then
    expect(container.querySelector("[aria-hidden]")).not.toBeNull();
    expect(screen.getByText("Dormant")).toBeVisible();
  });
});
