import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import type {
  EvidenceItem,
  SourceType,
} from "@/features/expertise/domain/expertise";
import { EvidenceDetails } from "@/features/expertise/ui/EvidenceDetails";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { areaEvidence: vi.fn(), areaEvidenceIn: vi.fn() },
}));

const gateway = vi.mocked(httpExpertiseGateway);

function item(overrides: Partial<EvidenceItem> = {}): EvidenceItem {
  return {
    signalType: "PRESENTED",
    sourceType: "SLOT",
    sourceId: 1,
    label: "Scaling Kafka",
    communitySlug: "eng",
    occurredAt: "2026-01-01T00:00:00Z",
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...overrides,
  };
}

interface RenderOptions {
  onDismiss?: (i: EvidenceItem) => void;
  tag?: string;
  totalCount?: number;
  slug?: string;
}

function renderList(items: EvidenceItem[], options: RenderOptions = {}) {
  const { onDismiss, tag, totalCount, slug } = options;
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <EvidenceDetails
          items={items}
          {...(onDismiss ? { onDismiss } : {})}
          {...(tag !== undefined ? { tag } : {})}
          {...(totalCount !== undefined ? { totalCount } : {})}
          slug={slug}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("EvidenceDetails", () => {
  it("shows an empty message when there is no evidence", () => {
    renderList([]);
    expect(screen.getByText(/no recent evidence/i)).toBeInTheDocument();
  });

  it.each<[SourceType, string]>([
    ["SLOT", "/communities/eng/schedule"],
    ["ARTIFACT", "/communities/eng/library?q=Scaling%20Kafka"],
    ["TOPIC", "/communities/eng/backlog"],
  ])("links a %s evidence row to its source", (sourceType, href) => {
    renderList([item({ sourceType })]);
    expect(
      screen.getByRole("link", { name: /Presented: Scaling Kafka/ }),
    ).toHaveAttribute("href", href);
  });

  it("links a closed action item to the community's backlog", () => {
    renderList([
      item({
        signalType: "COMPLETED",
        sourceType: "ACTION_ITEM",
        sourceId: 7,
        label: "Write the migration runbook",
        communitySlug: "backend-guild",
      }),
    ]);

    expect(
      screen.getByRole("link", { name: /Write the migration runbook/ }),
    ).toHaveAttribute("href", "/communities/backend-guild/backlog");
  });

  it("links an artifact with no title to the unfiltered library", () => {
    renderList([item({ sourceType: "ARTIFACT", label: null })]);
    expect(screen.getByRole("link", { name: /Presented/ })).toHaveAttribute(
      "href",
      "/communities/eng/library",
    );
  });

  it("links a slot to the session it happened in, with the real type badge", () => {
    renderList([
      item({
        sourceType: "SLOT",
        sessionId: 42,
        typeId: 7,
        typeLabel: "Workshop",
      }),
    ]);
    expect(
      screen.getByRole("link", { name: /Presented: Scaling Kafka/ }),
    ).toHaveAttribute("href", "/communities/eng/sessions/42");
  });

  it("falls back to the schedule when a slot's session is unknown", () => {
    renderList([item({ sourceType: "SLOT", sessionId: null })]);
    expect(
      screen.getByRole("link", { name: /Presented: Scaling Kafka/ }),
    ).toHaveAttribute("href", "/communities/eng/schedule");
  });

  it("renders plain text (no link) when the community slug is absent", () => {
    renderList([item({ communitySlug: null })]);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Presented: Scaling Kafka")).toBeInTheDocument();
  });

  it("shows only the signal type when there is no title", () => {
    renderList([item({ label: null })]);
    expect(screen.getByText("Presented")).toBeInTheDocument();
  });

  it("invokes onDismiss with the item when the dismiss control is clicked", async () => {
    const onDismiss = vi.fn();
    renderList([item({ communitySlug: null })], { onDismiss });

    await user.click(
      screen.getByRole("button", { name: "Dismiss Presented: Scaling Kafka" }),
    );
    expect(onDismiss).toHaveBeenCalledWith(
      expect.objectContaining({ sourceId: 1 }),
    );
  });

  it("omits the dismiss control when no handler is given", () => {
    renderList([item({ communitySlug: null })]);
    expect(
      screen.queryByRole("button", { name: /dismiss/i }),
    ).not.toBeInTheDocument();
  });

  it("does not offer older proof when tag/totalCount are omitted (practitioner view)", () => {
    renderList([item()]);
    expect(
      screen.queryByRole("button", { name: /show older proof/i }),
    ).not.toBeInTheDocument();
  });
});

describe("EvidenceDetails older proof paging", () => {
  function olderItem(id: number): EvidenceItem {
    return item({
      sourceId: id,
      label: `Older talk ${id}`,
      communitySlug: null,
    });
  }

  it("pages in older proof on demand and hides the control once exhausted", async () => {
    gateway.areaEvidence.mockResolvedValue({
      items: [olderItem(101), olderItem(102), olderItem(103)],
      hasMore: false,
    });

    const recent = Array.from({ length: 5 }, (_, i) =>
      item({ sourceId: i, label: `Recent ${i}`, communitySlug: null }),
    );
    renderList(recent, { tag: "kafka", totalCount: 8 });

    const showOlder = screen.getByRole("button", {
      name: "Show older proof (3 more)",
    });
    expect(gateway.areaEvidence).not.toHaveBeenCalled();

    await user.click(showOlder);

    await waitFor(() => {
      expect(gateway.areaEvidence).toHaveBeenCalledWith("kafka", 5, 20);
    });
    expect(
      await screen.findByText("Presented: Older talk 101"),
    ).toBeInTheDocument();
    expect(screen.getByText("Presented: Older talk 102")).toBeInTheDocument();
    expect(screen.getByText("Presented: Older talk 103")).toBeInTheDocument();

    expect(
      screen.queryByRole("button", { name: /show older proof/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /show more/i }),
    ).not.toBeInTheDocument();
  });

  it("pages a community page's older proof from that community alone", async () => {
    // given
    gateway.areaEvidenceIn.mockResolvedValue({
      items: [olderItem(201)],
      hasMore: false,
    });
    renderList([item()], { tag: "kafka", totalCount: 2, slug: "platform" });

    // when
    await user.click(
      screen.getByRole("button", { name: "Show older proof (1 more)" }),
    );

    // then
    await waitFor(() => {
      expect(gateway.areaEvidenceIn).toHaveBeenCalledWith(
        "platform",
        "kafka",
        1,
        20,
      );
    });
    expect(gateway.areaEvidence).not.toHaveBeenCalled();
    expect(
      await screen.findByText("Presented: Older talk 201"),
    ).toBeInTheDocument();
  });

  it("renders just the pager, not the empty state, when there is no recent evidence but older proof exists", () => {
    gateway.areaEvidence.mockResolvedValue({ items: [], hasMore: false });

    renderList([], { tag: "kafka", totalCount: 2 });

    expect(
      screen.getByRole("button", { name: "Show older proof (2 more)" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/no recent evidence/i)).not.toBeInTheDocument();
  });
});
