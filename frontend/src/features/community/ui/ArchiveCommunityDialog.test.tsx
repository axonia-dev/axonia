import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useArchiveCommunity } from "@/features/community/api/use-communities";
import { ArchiveCommunityDialog } from "@/features/community/ui/ArchiveCommunityDialog";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/community/api/use-communities", () => ({
  useArchiveCommunity: vi.fn(() => ({
    isPending: false,
    error: null,
    mutate: vi.fn(),
  })),
}));

function renderDialog(onOpenChange = vi.fn()) {
  render(
    <ArchiveCommunityDialog
      slug="backend-guild"
      isOpen
      onOpenChange={onOpenChange}
    />,
  );
}

describe("ArchiveCommunityDialog", () => {
  it("keeps Archive disabled until the slug is typed exactly", async () => {
    // when
    renderDialog();
    const archive = screen.getByRole("button", { name: "Archive" });

    // then — guarded out of the gate
    expect(archive).toBeDisabled();

    // when a near-miss is typed
    await user.type(screen.getByRole("textbox"), "backend-guil");

    // then — still guarded
    expect(archive).toBeDisabled();

    // when the exact slug is typed
    await user.type(screen.getByRole("textbox"), "d");

    // then — armed
    expect(archive).toBeEnabled();
  });

  it("archives once the slug matches and the button is pressed", async () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useArchiveCommunity).mockReturnValue({
      isPending: false,
      error: null,
      mutate,
    } as never);

    // when
    renderDialog();
    await user.type(screen.getByRole("textbox"), "backend-guild");
    await user.click(screen.getByRole("button", { name: "Archive" }));

    // then
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("closes itself after a successful archive", async () => {
    // given
    const onOpenChange = vi.fn();
    vi.mocked(useArchiveCommunity).mockReturnValue({
      isPending: false,
      error: null,
      mutate: vi.fn((_vars, opts?: { onSuccess?: () => void }) =>
        opts?.onSuccess?.(),
      ),
    } as never);

    // when
    renderDialog(onOpenChange);
    await user.type(screen.getByRole("textbox"), "backend-guild");
    await user.click(screen.getByRole("button", { name: "Archive" }));

    // then
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("surfaces a failure without closing", () => {
    // given
    vi.mocked(useArchiveCommunity).mockReturnValue({
      isPending: false,
      error: new Error("boom"),
      mutate: vi.fn(),
    } as never);

    // when
    renderDialog();

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("asks to close when Cancel is pressed", async () => {
    // given
    const onOpenChange = vi.fn();

    // when
    renderDialog(onOpenChange);
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
