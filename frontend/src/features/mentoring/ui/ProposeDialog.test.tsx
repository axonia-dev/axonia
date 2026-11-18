import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePropose } from "@/features/mentoring/api/use-mentoring";
import type { MentoringRole } from "@/features/mentoring/domain/mentoring";
import { ProposeDialog } from "@/features/mentoring/ui/ProposeDialog";
import { ApiError } from "@/lib/api-client";
import { mutation } from "@/test/mentoring-page-view";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  usePropose: vi.fn(),
}));

const person = {
  userId: "u-1",
  displayName: "Ada",
  areas: ["kafka", "testing"],
};

function open(asRole: MentoringRole = "MENTEE") {
  const onOpenChange = vi.fn();
  render(
    <ProposeDialog
      slug="platform"
      person={person}
      asRole={asRole}
      isOpen
      onOpenChange={onOpenChange}
    />,
  );
  return onOpenChange;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(usePropose).mockReturnValue(mutation() as never);
});

describe("ProposeDialog", () => {
  it("sends the picked and typed topics with the message", async () => {
    // given
    const propose = mutation();
    vi.mocked(usePropose).mockReturnValue(propose as never);
    open();

    // when
    await userEvent.click(screen.getByRole("button", { name: "kafka" }));
    await userEvent.type(
      screen.getByRole("textbox", { name: "Another topic" }),
      "Giving feedback{Enter}",
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "Message" }),
      "Happy to pair up?",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Ask for mentoring" }),
    );

    // then
    expect(screen.getByText("Topics (2 of 5)")).toBeInTheDocument();
    expect(propose.mutate).toHaveBeenCalledWith(
      {
        toUserId: "u-1",
        asRole: "MENTEE",
        topics: ["kafka", "Giving feedback"],
        message: "Happy to pair up?",
      },
      expect.anything(),
    );
  });

  it("will not send without a topic", async () => {
    // given
    open();

    // when
    await userEvent.type(
      screen.getByRole("textbox", { name: "Message" }),
      "Hello",
    );

    // then
    expect(
      screen.getByRole("button", { name: "Ask for mentoring" }),
    ).toBeDisabled();
  });

  it("names the field Topic until a first topic is added", async () => {
    // given
    open();

    // when
    await userEvent.type(
      screen.getByRole("textbox", { name: "Topic" }),
      "Kafka{Enter}",
    );

    // then
    expect(screen.getByRole("textbox", { name: "Another topic" })).toHaveValue(
      "",
    );
  });

  it("sends a typed topic that was never added rather than dropping it", async () => {
    // given
    const propose = mutation();
    vi.mocked(usePropose).mockReturnValue(propose as never);
    open();

    // when
    await userEvent.click(screen.getByRole("button", { name: "kafka" }));
    await userEvent.type(
      screen.getByRole("textbox", { name: "Another topic" }),
      "Kafka consumer groups",
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "Message" }),
      "Hi",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Ask for mentoring" }),
    );

    // then
    expect(propose.mutate).toHaveBeenCalledWith(
      expect.objectContaining({ topics: ["kafka", "Kafka consumer groups"] }),
      expect.anything(),
    );
  });

  it("can send with only a typed topic", async () => {
    // given
    open();

    // when
    await userEvent.type(screen.getByRole("textbox", { name: "Topic" }), "x");
    await userEvent.type(
      screen.getByRole("textbox", { name: "Message" }),
      "Hi",
    );

    // then
    expect(
      screen.getByRole("button", { name: "Ask for mentoring" }),
    ).toBeEnabled();
  });

  it("refuses a sixth topic", async () => {
    // given
    open();
    const field = screen.getByRole("textbox", { name: "Topic" });
    for (const topic of ["a", "b", "c", "d", "e"]) {
      await userEvent.type(field, topic);
      await userEvent.click(screen.getByRole("button", { name: "Add topic" }));
    }

    // when
    await userEvent.type(field, "f");
    await userEvent.type(
      screen.getByRole("textbox", { name: "Message" }),
      "Hi",
    );

    // then
    expect(screen.getByText("Topics (5 of 5)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add topic" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Ask for mentoring" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "kafka" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    await userEvent.click(screen.getByRole("button", { name: "kafka" }));
    expect(screen.getByText("Topics (5 of 5)")).toBeInTheDocument();
  });

  it("shows the mentor full message on MENTORING_MENTOR_FULL", () => {
    // given
    vi.mocked(usePropose).mockReturnValue(
      mutation({
        isError: true,
        error: new ApiError(409, "full", "MENTORING_MENTOR_FULL"),
      }) as never,
    );

    // when
    open();

    // then
    expect(
      screen.getByText("This mentor has no room for another mentee right now."),
    ).toBeInTheDocument();
  });

  it.each([
    ["MENTEE" as const, "Ask for mentoring from Ada", "Ask for mentoring"],
    ["MENTOR" as const, "Offer mentoring to Ada", "Offer mentoring"],
  ])("as %s is titled %s", (asRole, title, send) => {
    // when
    open(asRole);

    // then
    expect(screen.getByRole("heading", { name: title })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: send })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Once accepted, the pairing shows on the community’s Mentoring page.",
      ),
    ).toBeInTheDocument();
  });
});
