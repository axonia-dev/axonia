import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSetOptOut } from "@/features/expertise/api/use-expertise-prefs";
import { VisibilityMenu } from "@/features/expertise/ui/VisibilityMenu";

vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useSetOptOut: vi.fn(),
}));

const mockUseSetOptOut = vi.mocked(useSetOptOut);

function mutation(mutate = vi.fn()) {
  return { mutate, isPending: false } as never;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseSetOptOut.mockReturnValue(mutation());
});

describe("VisibilityMenu", () => {
  it("surfaces the hidden count in the trigger label", () => {
    render(<VisibilityMenu optedOut={false} hiddenCount={2} />);
    expect(
      screen.getByRole("button", { name: /2 hidden/i }),
    ).toBeInTheDocument();
  });

  it("opts out through a confirm dialog", async () => {
    const mutate = vi.fn();
    mockUseSetOptOut.mockReturnValue(mutation(mutate));
    render(<VisibilityMenu optedOut={false} hiddenCount={0} />);

    await user.click(
      screen.getByRole("button", { name: /visible to members/i }),
    );
    await user.click(screen.getByRole("button", { name: /^opt out$/i }));

    expect(mutate).toHaveBeenCalledWith(true);
  });

  it("offers opt-back-in when already opted out", async () => {
    const mutate = vi.fn();
    mockUseSetOptOut.mockReturnValue(mutation(mutate));
    render(<VisibilityMenu optedOut hiddenCount={0} />);

    await user.click(
      screen.getByRole("button", { name: /hidden from members/i }),
    );
    await user.click(screen.getByRole("button", { name: /opt back in/i }));

    expect(mutate).toHaveBeenCalledWith(false);
  });
});
