import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRemoveChatDestination } from "@/features/community/api/use-chat-destinations";
import type { ChatDestination } from "@/features/community/domain/chat-destination";
import { ApiError } from "@/lib/api-client";

import { RemoveDestinationDialog } from "./RemoveDestinationDialog";

vi.mock("@/features/community/api/use-chat-destinations", () => ({
  useRemoveChatDestination: vi.fn(),
}));

const DESTINATION: ChatDestination = {
  id: 7,
  name: "#guild",
  platform: "SLACK",
  maskedUrl: "hooks.slack.com/...abcd",
  broken: false,
  lastPostedAt: null,
  lastFailureAt: null,
  lastError: null,
};

const removeMutate = vi.fn();

function stub(overrides?: {
  isPending?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useRemoveChatDestination).mockReturnValue({
    mutate: removeMutate,
    isPending: overrides?.isPending ?? false,
    isError: overrides?.isError ?? false,
    error: overrides?.error ?? null,
  } as never);
}

function renderDialog({
  destination = DESTINATION,
  isOpen = true,
  resetKey = 0,
  onOpenChange = vi.fn(),
}: {
  destination?: ChatDestination | null;
  isOpen?: boolean;
  resetKey?: number;
  onOpenChange?: (open: boolean) => void;
} = {}) {
  render(
    <RemoveDestinationDialog
      slug="backend-guild"
      destination={destination}
      isOpen={isOpen}
      resetKey={resetKey}
      onOpenChange={onOpenChange}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stub();
});

describe("RemoveDestinationDialog", () => {
  it("names the destination in the heading and explains removal stops posting only", () => {
    renderDialog();

    expect(
      screen.getByRole("heading", { name: "Remove #guild?" }),
    ).toBeVisible();
    expect(
      screen.getByText(
        "axonia stops posting there. Nothing already posted is removed.",
      ),
    ).toBeVisible();
  });

  it("removes the destination on confirm", async () => {
    renderDialog();

    await user.click(screen.getByRole("button", { name: "Remove" }));

    expect(removeMutate).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
  });

  it("does nothing on cancel", async () => {
    renderDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(removeMutate).not.toHaveBeenCalled();
  });

  it("shows a human message when removal fails", () => {
    stub({
      isError: true,
      error: new ApiError(500, "x", "SOME_UNKNOWN_CODE"),
    });

    renderDialog();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });
});
