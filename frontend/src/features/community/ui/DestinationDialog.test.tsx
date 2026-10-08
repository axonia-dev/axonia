import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateChatDestination,
  useUpdateChatDestination,
} from "@/features/community/api/use-chat-destinations";
import type { ChatDestination } from "@/features/community/domain/chat-destination";
import { ApiError } from "@/lib/api-client";

import { DestinationDialog } from "./DestinationDialog";

vi.mock("@/features/community/api/use-chat-destinations", () => ({
  useCreateChatDestination: vi.fn(),
  useUpdateChatDestination: vi.fn(),
}));

function healthy(overrides: Partial<ChatDestination> = {}): ChatDestination {
  return {
    id: 1,
    name: "#guild",
    platform: "SLACK",
    maskedUrl: "hooks.slack.com/...abcd",
    broken: false,
    lastPostedAt: null,
    lastFailureAt: null,
    lastError: null,
    ...overrides,
  };
}

function renderDialog({
  destination,
  onOpenChange = vi.fn(),
  onSaved = vi.fn(),
  createError = null,
}: {
  destination: ChatDestination | null;
  onOpenChange?: (open: boolean) => void;
  onSaved?: () => void;
  createError?: ApiError | null;
}) {
  const create = vi.fn();
  const update = vi.fn();
  vi.mocked(useCreateChatDestination).mockReturnValue({
    mutate: create,
    isPending: false,
    isError: createError !== null,
    error: createError,
  } as never);
  vi.mocked(useUpdateChatDestination).mockReturnValue({
    mutate: update,
    isPending: false,
    isError: false,
    error: null,
  } as never);

  render(
    <DestinationDialog
      slug="backend-guild"
      destination={destination}
      isOpen
      resetKey={0}
      onOpenChange={onOpenChange}
      onSaved={onSaved}
    />,
  );

  return { create, update };
}

/** Simulates a resolved mutation by invoking the caller's own `onSuccess`,
 *  exactly as TanStack Query does once the real request lands. */
function resolves(mutate: ReturnType<typeof vi.fn>) {
  mutate.mockImplementation(
    (_variables: unknown, options?: { onSuccess?: () => void }) => {
      options?.onSuccess?.();
    },
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("DestinationDialog", () => {
  it("states the consent sentence once, above the confirm button", () => {
    renderDialog({ destination: null });

    expect(
      screen.getByText("Posts carry the same content as the email."),
    ).toBeVisible();
  });

  it("never prefills the webhook url when editing, and says so", () => {
    renderDialog({
      destination: healthy({
        name: "#guild",
        maskedUrl: "hooks.slack.com/...a4f2",
      }),
    });

    expect(screen.getByLabelText("Name")).toHaveValue("#guild");
    expect(screen.getByLabelText("Webhook URL")).toHaveValue("");
    expect(
      screen.getByText("Leave empty to keep hooks.slack.com/...a4f2."),
    ).toBeVisible();
  });

  /**
   * A Power Automate mask runs past eighty characters, so naming it in the
   * placeholder would clip it - and clip the tail first, which is the only part
   * distinguishing two destinations in one environment. The description wraps,
   * so it survives whole.
   */
  it("shows a long stored address in full rather than clipping it", () => {
    const mask =
      "11223344-5566-7788-99aa-bbccddeeff00.1f.environment.api.powerplatform.com/...1234";
    renderDialog({
      destination: healthy({ platform: "TEAMS", maskedUrl: mask }),
    });

    expect(screen.getByText(`Leave empty to keep ${mask}.`)).toBeVisible();
    expect(screen.queryByPlaceholderText(new RegExp(mask))).toBeNull();
  });

  it("sends a null webhook url when the field is left empty, and closes on success", async () => {
    const onOpenChange = vi.fn();
    const { update } = renderDialog({
      destination: healthy({ id: 7, name: "#guild" }),
      onOpenChange,
    });
    resolves(update);

    await user.clear(screen.getByLabelText("Name"));
    await user.type(screen.getByLabelText("Name"), "#renamed");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(update).toHaveBeenCalledWith(
      {
        id: 7,
        input: { name: "#renamed", platform: "SLACK", webhookUrl: null },
      },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
    // Proves `onSuccess` was actually wired to the dialog's own close, not
    // merely present in the call: the caller's `onOpenChange` only fires
    // through `close()`, which the render-prop hands to the mutation.
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("calls onSaved once an update actually succeeds, so stale state elsewhere can clear", async () => {
    const onSaved = vi.fn();
    const { update } = renderDialog({
      destination: healthy({ id: 7, name: "#guild" }),
      onSaved,
    });
    resolves(update);

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(onSaved).toHaveBeenCalled();
  });

  it("sends the typed name and url to create when adding", async () => {
    const onOpenChange = vi.fn();
    const { create } = renderDialog({ destination: null, onOpenChange });
    resolves(create);

    await user.type(screen.getByLabelText("Name"), "#platform-guild");
    await user.type(
      screen.getByLabelText("Webhook URL"),
      "https://hooks.slack.com/services/new",
    );
    await user.click(screen.getByRole("button", { name: "Add" }));

    expect(create).toHaveBeenCalledWith(
      {
        name: "#platform-guild",
        platform: "SLACK",
        webhookUrl: "https://hooks.slack.com/services/new",
      },
      expect.objectContaining({ onSuccess: expect.any(Function) as unknown }),
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  /**
   * The defect this picker exists for: a leader configuring Teams was shown a
   * Slack placeholder and a hint naming Slack first, with nothing in the dialog
   * acknowledging the other tool until the save had already been refused.
   */
  it("re-points the url field's placeholder and hint at the chosen chat tool", async () => {
    renderDialog({ destination: null });

    expect(
      screen.getByPlaceholderText("https://hooks.slack.com/services/..."),
    ).toBeVisible();

    await user.click(screen.getByRole("radio", { name: "Microsoft Teams" }));

    expect(
      screen.getByPlaceholderText(/environment\.api\.powerplatform\.com/),
    ).toBeVisible();
    expect(screen.getByText(/Power Automate/)).toBeVisible();
  });

  it("sends the chosen chat tool to create", async () => {
    const { create } = renderDialog({ destination: null });
    resolves(create);

    await user.type(screen.getByLabelText("Name"), "#eng-leads");
    await user.click(screen.getByRole("radio", { name: "Microsoft Teams" }));
    await user.type(
      screen.getByLabelText("Webhook URL"),
      "https://x.1f.environment.api.powerplatform.com/powerautomate/x",
    );
    await user.click(screen.getByRole("button", { name: "Add" }));

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ platform: "TEAMS" }),
      expect.anything(),
    );
  });

  /**
   * One error code, two messages. The backend cannot be quoted here because the
   * frontend owns its copy, so the code is resolved against the picker's own
   * value - which is what makes the refusal actionable rather than a restatement
   * that something was wrong.
   */
  it("names the chosen chat tool when the url is refused", async () => {
    renderDialog({
      destination: null,
      createError: new ApiError(400, "nope", "INVALID_CHAT_WEBHOOK_URL"),
    });

    expect(screen.getByText(/not a Slack incoming webhook URL/)).toBeVisible();

    await user.click(screen.getByRole("radio", { name: "Microsoft Teams" }));

    expect(screen.getByText(/not a Microsoft Teams webhook URL/)).toBeVisible();
  });

  it("keeps Add disabled until both a name and a url are typed", async () => {
    renderDialog({ destination: null });

    const addButton = screen.getByRole("button", { name: "Add" });
    expect(addButton).toBeDisabled();

    await user.type(screen.getByLabelText("Name"), "#platform-guild");
    expect(addButton).toBeDisabled();

    await user.type(
      screen.getByLabelText("Webhook URL"),
      "https://hooks.slack.com/services/new",
    );
    expect(addButton).toBeEnabled();
  });
});
