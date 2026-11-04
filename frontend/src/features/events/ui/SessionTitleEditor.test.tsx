import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRenameSession } from "@/features/events/api/use-sessions";
import { SessionTitleEditor } from "@/features/events/ui/SessionTitleEditor";
import { ApiError } from "@/lib/api-client";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-sessions", () => ({
  useRenameSession: vi.fn(),
}));

const mutate = vi.fn();
const reset = vi.fn();

function renameState(overrides: Record<string, unknown> = {}) {
  vi.mocked(useRenameSession).mockReturnValue({
    mutate,
    reset,
    isPending: false,
    variables: undefined,
    error: null,
    ...overrides,
  } as unknown as ReturnType<typeof useRenameSession>);
}

const named = aScheduledSession({
  id: 4,
  title: "S26E10 - DevTool",
  displayTitle: "S26E10 - DevTool",
  automaticTitle: "P1s",
});

const unnamed = aScheduledSession({
  id: 4,
  title: null,
  displayTitle: "P1s",
  automaticTitle: "P1s",
});

function renderEditor(session = named, canRename = true) {
  return render(
    <SessionTitleEditor
      slug="backend-guild"
      session={session}
      canRename={canRename}
      morphId={undefined}
    />,
  );
}

async function startEditing() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "S26E10 - DevTool" }));
  return {
    user,
    input: screen.getByRole("textbox", { name: "Session title" }),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  renameState();
});

describe("SessionTitleEditor", () => {
  it("names the session in the page heading", () => {
    // when
    renderEditor();

    // then
    expect(
      screen.getByRole("heading", { level: 2, name: "S26E10 - DevTool" }),
    ).toBeInTheDocument();
  });

  it("offers no editing to a reader who cannot manage the schedule", () => {
    // when
    renderEditor(named, false);

    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "S26E10 - DevTool" }),
    ).toBeInTheDocument();
  });

  it("opens the title for editing, selected and with the automatic name as the placeholder", async () => {
    // given
    renderEditor();

    // when
    const { input } = await startEditing();

    // then
    expect(input).toHaveFocus();
    expect(input).toHaveValue("S26E10 - DevTool");
    expect(input).toHaveAttribute("placeholder", "P1s");
    expect(input).toHaveAttribute("maxLength", "255");
    expect((input as HTMLInputElement).selectionStart).toBe(0);
    expect((input as HTMLInputElement).selectionEnd).toBe(16);
  });

  it("opens an untitled session empty, so typing names it rather than appending to the automatic name", async () => {
    // given
    renderEditor(unnamed);
    const user = userEvent.setup();

    // when
    await user.click(screen.getByRole("button", { name: "P1s" }));

    // then
    expect(screen.getByRole("textbox", { name: "Session title" })).toHaveValue(
      "",
    );
  });

  it("saves the trimmed title on Enter", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();

    // when
    await user.clear(input);
    await user.type(input, "  S26E11 - Kafka  {Enter}");

    // then
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0]?.[0]).toBe("S26E11 - Kafka");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("sends one request when Enter is followed by leaving the field", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "S26E11 - Kafka");

    // when
    await user.keyboard("{Enter}");
    await user.tab();

    // then
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("clears a refused rename's message once the title is opened again", async () => {
    // given
    renameState({
      error: new ApiError(400, "too long", "SESSION_TITLE_TOO_LONG"),
    });
    renderEditor();

    // when
    await startEditing();

    // then
    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("saves on blur", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "S26E11 - Kafka");

    // when
    await user.tab();

    // then
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate.mock.calls[0]?.[0]).toBe("S26E11 - Kafka");
  });

  it("clears the title when emptied, handing the session back its automatic name", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();

    // when
    await user.clear(input);
    await user.keyboard("{Enter}");

    // then
    expect(mutate.mock.calls[0]?.[0]).toBeNull();
  });

  it("sends nothing when the title did not change", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();

    // when
    await user.type(input, "{End}  {Enter}");

    // then
    expect(mutate).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "S26E10 - DevTool" }),
    ).toHaveFocus();
  });

  it("drops the edit on Escape and returns focus to the title", async () => {
    // given
    renderEditor();
    const { user, input } = await startEditing();
    await user.type(input, " draft");

    // when
    await user.keyboard("{Escape}");

    // then
    expect(mutate).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "S26E10 - DevTool" }),
    ).toHaveFocus();
  });

  it("shows the new name while it is being saved", () => {
    // given
    renameState({ isPending: true, variables: "S26E11 - Kafka" });

    // when
    renderEditor();

    // then
    expect(
      screen.getByRole("heading", { level: 2, name: "S26E11 - Kafka" }),
    ).toBeInTheDocument();
  });

  it("shows the automatic name while a clear is being saved", () => {
    // given
    renameState({ isPending: true, variables: null });

    // when
    renderEditor();

    // then
    expect(
      screen.getByRole("heading", { level: 2, name: "P1s" }),
    ).toBeInTheDocument();
  });

  it("says why a rename was refused", () => {
    // given
    renameState({
      error: new ApiError(400, "too long", "SESSION_TITLE_TOO_LONG"),
    });

    // when
    renderEditor();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "A session title is at most 255 characters.",
    );
  });
});
