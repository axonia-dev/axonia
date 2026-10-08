import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { CaptureArtifactButton } from "@/features/knowledge/ui/CaptureArtifactButton";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

// Stub the form: expose a trigger that fires onCaptured, the success path the
// button reacts to. The form owns its own hook + query invalidation.
vi.mock("@/features/knowledge/ui/CaptureArtifactForm", () => ({
  CaptureArtifactForm: ({
    onCaptured,
  }: {
    onCaptured: (artifact: { id: number }) => void;
  }) => (
    <button type="button" onClick={() => onCaptured({ id: 1 })}>
      do-capture
    </button>
  ),
}));

describe("CaptureArtifactButton", () => {
  it("opens the capture dialog", async () => {
    // given
    render(<CaptureArtifactButton slug="eng" />);
    expect(screen.queryByRole("dialog")).toBeNull();

    // when
    await user.click(screen.getByRole("button", { name: /capture artifact/i }));

    // then
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("closes the dialog once an artifact is captured", async () => {
    // given
    render(<CaptureArtifactButton slug="eng" />);
    await user.click(screen.getByRole("button", { name: /capture artifact/i }));

    // when
    await user.click(screen.getByRole("button", { name: "do-capture" }));

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
