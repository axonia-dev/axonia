import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  SelfArea,
  SelfDeclaration,
  Tier,
} from "@/features/expertise/domain/expertise";
import { TierLane } from "@/features/expertise/ui/TierLane";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function area(tag: string, tier: Tier): SelfArea {
  return {
    canonicalTag: tag,
    tier,
    hidden: false,
    communities: [],
    evidence: {
      presentedCount: 1,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
  };
}

const handlers = {
  onHide: vi.fn(),
  onUnhide: vi.fn(),
  onDismiss: vi.fn(),
  declarations: [],
};

const KAFKA_IN_PLATFORM: SelfDeclaration = {
  tag: "kafka",
  communityId: 1,
  communityName: "Platform",
  communitySlug: "platform",
  hidden: false,
  declaredAt: "2026-09-12T12:00:00Z",
};

describe("TierLane", () => {
  it("shows the tier label and area count", () => {
    render(
      <TierLane
        tier="PRACTITIONER"
        areas={[area("kafka", "PRACTITIONER"), area("k8s", "PRACTITIONER")]}
        defaultOpen
        {...handlers}
      />,
    );
    expect(screen.getByText("Practitioner")).toBeInTheDocument();
    expect(screen.getByText("· 2")).toBeInTheDocument();
    expect(screen.getByText("kafka")).toBeInTheDocument();
  });

  it("renders nothing when the tier has no areas", () => {
    const { container } = render(
      <TierLane tier="INTERESTED" areas={[]} defaultOpen {...handlers} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("stays collapsed when defaultOpen is false and expands on click", async () => {
    render(
      <TierLane
        tier="INTERESTED"
        areas={[area("terraform", "INTERESTED")]}
        defaultOpen={false}
        {...handlers}
      />,
    );

    expect(screen.queryByText("terraform")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Interested/ }));
    expect(screen.getByText("terraform")).toBeInTheDocument();
  });

  it("windows areas and reveals the rest on Show more", async () => {
    const areas = Array.from({ length: 9 }, (_, i) =>
      area(`tag-${i}`, "PRACTITIONER"),
    );
    render(
      <TierLane
        tier="PRACTITIONER"
        areas={areas}
        defaultOpen
        pageSize={6}
        {...handlers}
      />,
    );

    for (let i = 0; i < 6; i += 1) {
      expect(screen.getByText(`tag-${i}`)).toBeInTheDocument();
    }
    for (let i = 6; i < 9; i += 1) {
      expect(screen.queryByText(`tag-${i}`)).not.toBeInTheDocument();
    }
    const showMore = screen.getByRole("button", { name: "Show 3 more" });

    await user.click(showMore);

    for (let i = 0; i < 9; i += 1) {
      expect(screen.getByText(`tag-${i}`)).toBeInTheDocument();
    }
    expect(
      screen.queryByRole("button", { name: /Show \d+ more/ }),
    ).not.toBeInTheDocument();
  });

  it("marks an area declared in its own community, removable when a handler is given", async () => {
    // given
    const onRemoveDeclaration = vi.fn();
    const kafka = {
      ...area("kafka", "PRACTITIONER"),
      communities: [{ id: 1, slug: "platform", name: "Platform" }],
    };
    render(
      <TierLane
        tier="PRACTITIONER"
        areas={[kafka, area("k8s", "PRACTITIONER")]}
        defaultOpen
        {...handlers}
        declarations={[KAFKA_IN_PLATFORM]}
        onRemoveDeclaration={onRemoveDeclaration}
      />,
    );

    // when
    await user.click(
      screen.getByRole("button", {
        name: "Stop being open to questions on kafka",
      }),
    );

    // then
    expect(screen.getAllByText("Open to questions")).toHaveLength(1);
    expect(onRemoveDeclaration).toHaveBeenCalledWith("kafka");
  });

  it("marks without a removal where no handler is given", () => {
    // when
    render(
      <TierLane
        tier="PRACTITIONER"
        areas={[
          {
            ...area("kafka", "PRACTITIONER"),
            communities: [{ id: 1, slug: "platform", name: "Platform" }],
          },
        ]}
        defaultOpen
        {...handlers}
        declarations={[KAFKA_IN_PLATFORM]}
      />,
    );

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Stop being open to questions/ }),
    ).not.toBeInTheDocument();
  });
});
