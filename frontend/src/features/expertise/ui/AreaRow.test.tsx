import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SelfArea } from "@/features/expertise/domain/expertise";
import { AreaRow } from "@/features/expertise/ui/AreaRow";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function area(overrides: Partial<SelfArea> = {}): SelfArea {
  return {
    canonicalTag: "kafka",
    tier: "PRACTITIONER",
    hidden: false,
    communities: [],
    evidence: {
      presentedCount: 1,
      contributedCount: 0,
      offeredCount: 0,
      recent: [
        {
          signalType: "PRESENTED",
          sourceType: "SLOT",
          sourceId: 1,
          label: "Scaling Kafka",
          communitySlug: null,
          occurredAt: "2026-01-01T00:00:00Z",
          sessionId: null,
          typeId: null,
          typeLabel: null,
          typeIcon: null,
          typeAccentColor: null,
        },
      ],
    },
    communityTiers: [],
    ...overrides,
  };
}

const noop = vi.fn();

describe("AreaRow", () => {
  it("hides a visible area via the eye control", async () => {
    const onHide = vi.fn();
    render(
      <AreaRow
        area={area()}
        onHide={onHide}
        onUnhide={noop}
        onDismiss={noop}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Hide kafka" }));

    expect(onHide).toHaveBeenCalledWith("kafka");
  });

  it("shows a hidden area and unhides it via the eye control", async () => {
    const onUnhide = vi.fn();
    render(
      <AreaRow
        area={area({ hidden: true })}
        onHide={noop}
        onUnhide={onUnhide}
        onDismiss={noop}
      />,
    );

    expect(screen.getByText(/hidden from others/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Show kafka" }));

    expect(onUnhide).toHaveBeenCalledWith("kafka");
  });

  it("expands to reveal evidence and routes dismiss with the tag", async () => {
    const onDismiss = vi.fn();
    render(
      <AreaRow
        area={area()}
        onHide={noop}
        onUnhide={noop}
        onDismiss={onDismiss}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Show evidence" }));
    expect(screen.getByText("Presented: Scaling Kafka")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Dismiss Presented: Scaling Kafka" }),
    );

    expect(onDismiss).toHaveBeenCalledWith(
      expect.objectContaining({ sourceId: 1 }),
      "kafka",
    );
  });

  it("renders the marker it is given", () => {
    // when
    render(
      <AreaRow
        area={area()}
        onHide={noop}
        onUnhide={noop}
        onDismiss={noop}
        marker={<span>marker here</span>}
      />,
    );

    // then
    expect(screen.getByText("marker here")).toBeInTheDocument();
  });
});
