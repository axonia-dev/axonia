import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCalendarSyncStatus } from "@/features/events/api/use-calendar-subscription";
import { CalendarSyncChip } from "@/features/events/ui/CalendarSyncChip";

vi.mock("@/features/events/api/use-calendar-subscription", () => ({
  useCalendarSyncStatus: vi.fn(),
}));
vi.mock("@/lib/use-wake-at", () => ({
  useNow: () => new Date("2027-01-13T12:00:00Z"),
}));

const mockStatus = vi.mocked(useCalendarSyncStatus);

function answer(
  data: {
    url: null;
    lastFetchedAt: string | null;
    lastFetchedAgent: string | null;
  } | null,
) {
  mockStatus.mockReturnValue({ data } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
});

describe("CalendarSyncChip", () => {
  it("renders nothing without a subscription", () => {
    answer(null);
    const { container } = render(
      <CalendarSyncChip slug="dojo" onManage={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("states when the provider last synced and who it was", () => {
    answer({
      url: null,
      lastFetchedAt: "2027-01-13T10:00:00Z",
      lastFetchedAgent: "Microsoft Office/16.0",
    });
    render(<CalendarSyncChip slug="dojo" onManage={vi.fn()} />);

    expect(
      screen.getByRole("button", { name: "Synced 2 hours ago, by Microsoft" }),
    ).toBeInTheDocument();
  });

  it("says a new subscription is waiting for its first sync", () => {
    answer({ url: null, lastFetchedAt: null, lastFetchedAgent: null });
    render(<CalendarSyncChip slug="dojo" onManage={vi.fn()} />);

    expect(
      screen.getByRole("button", { name: /Waiting for the first sync/ }),
    ).toBeInTheDocument();
  });

  it("explains that the provider owns the timing, and leads to the link settings", async () => {
    const onManage = vi.fn();
    answer({
      url: null,
      lastFetchedAt: "2027-01-13T10:00:00Z",
      lastFetchedAgent: "Microsoft Office/16.0",
    });
    render(<CalendarSyncChip slug="dojo" onManage={onManage} />);

    await user.click(screen.getByRole("button", { name: /Synced/ }));

    expect(
      screen.getByText(/Microsoft fetches this schedule on its own timing/),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Calendar link settings" }),
    );

    expect(onManage).toHaveBeenCalledOnce();
  });

  it("flags a sync older than a day", async () => {
    answer({
      url: null,
      lastFetchedAt: "2027-01-10T12:00:00Z",
      lastFetchedAgent: "Microsoft Office/16.0",
    });
    render(<CalendarSyncChip slug="dojo" onManage={vi.fn()} />);

    await user.click(
      screen.getByRole("button", { name: /Last synced 3 days ago/ }),
    );

    expect(
      screen.getByText(/has not fetched this schedule for over a day/),
    ).toBeInTheDocument();
  });
});
