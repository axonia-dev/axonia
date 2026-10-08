import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSettledActionItems } from "@/features/events/api/use-action-items";
import { SettledActionItems } from "@/features/events/ui/SettledActionItems";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/features/events/api/use-action-items", () => ({
  useSettledActionItems: vi.fn(),
}));

const mockUseSettled = vi.mocked(useSettledActionItems);

function mockSettled(overrides = {}) {
  mockUseSettled.mockReturnValue({
    items: [],
    total: 0,
    isLoading: false,
    isError: false,
    ...overrides,
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mockSettled();
});

const closed = anActionItem({
  id: 1,
  title: "Renew the certificate",
  outcome: "DONE",
  updatedAt: "2026-09-10T09:00:00Z",
});
const dropped = anActionItem({
  id: 2,
  title: "Migrate the old dashboards",
  outcome: "DROPPED",
  updatedAt: "2026-09-18T09:00:00Z",
});

describe("SettledActionItems", () => {
  /** Before this the only surface for a settled item was the session that raised it, and an item
   *  raised outside a session had none at all. */
  it("lists what closed and what was dropped side by side", () => {
    mockSettled({ items: [dropped, closed], total: 2 });

    render(<SettledActionItems slug="backend-guild" />);

    expect(screen.getByText("Renew the certificate")).toBeInTheDocument();
    expect(screen.getByText("Migrate the old dashboards")).toBeInTheDocument();
  });

  /** "Closed" and "dropped" are two different records: work that landed, and work the community
   *  settled it would not do. A single "closed" chip would erase the difference. */
  it("says which of the two outcomes each item reached", () => {
    mockSettled({ items: [dropped, closed], total: 2 });

    render(<SettledActionItems slug="backend-guild" />);

    expect(screen.getByText("Done")).toBeInTheDocument();
    expect(screen.getByText("Won’t do")).toBeInTheDocument();
  });

  it("says so when the server held more than the page it returned", () => {
    mockSettled({ items: [dropped, closed], total: 41 });

    render(<SettledActionItems slug="backend-guild" />);

    expect(screen.getByText(/Showing the first 2 of 41/)).toBeInTheDocument();
  });

  it("renders nothing when the community has settled nothing", () => {
    const { container } = render(<SettledActionItems slug="backend-guild" />);

    expect(container).toBeEmptyDOMElement();
  });

  /** A failed read must never render as "nothing settled yet": that is the opposite of the truth,
   *  with no other view to catch it against. */
  it("says the read failed rather than showing an empty record", () => {
    mockSettled({ isError: true });

    render(<SettledActionItems slug="backend-guild" />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      /Could not load settled action items/,
    );
  });
});
