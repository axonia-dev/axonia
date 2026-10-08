import { act, render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const fetchNextPage = vi.fn();
const state = { hasNextPage: true };

vi.mock("../api/use-notifications", () => ({
  useNotifications: () => ({
    data: {
      pages: [
        {
          entries: [
            {
              id: 9,
              sender: "Frontend Guild",
              headline: "Frontend Dojo",
              line: null,
              actionUrl: null,
              sentAt: "2026-08-03T10:00:00Z",
              read: true,
            },
          ],
          hasMore: state.hasNextPage,
        },
      ],
    },
    isSuccess: true,
    hasNextPage: state.hasNextPage,
    isFetchingNextPage: false,
    fetchNextPage,
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
  useMarkAllRead: () => ({ mutate: vi.fn() }),
}));

import { NotificationPanel } from "./NotificationPanel";

type Callback = (
  entries: IntersectionObserverEntry[],
  observer: IntersectionObserver,
) => void;

const callbacks: Callback[] = [];

// jsdom has no IntersectionObserver, and the shared setup's stub is a no-op that can never
// fire. This one hands the callback back so a test can say "the foot of the list came into
// view" without any layout to scroll.
class RecordingObserver {
  constructor(callback: Callback) {
    callbacks.push(callback);
  }
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}

function intersect(isIntersecting: boolean) {
  act(() => {
    for (const callback of callbacks) {
      callback([{ isIntersecting } as IntersectionObserverEntry], {
        disconnect: () => {},
      } as IntersectionObserver);
    }
  });
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <NotificationPanel />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  callbacks.length = 0;
  state.hasNextPage = true;
  vi.clearAllMocks();
  vi.stubGlobal("IntersectionObserver", RecordingObserver);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("NotificationPanel paging", () => {
  it("loads the next twenty when the foot of the list comes into view", () => {
    renderPanel();

    intersect(true);

    expect(fetchNextPage).toHaveBeenCalled();
  });

  it("loads nothing while the foot of the list is out of view", () => {
    renderPanel();

    intersect(false);

    expect(fetchNextPage).not.toHaveBeenCalled();
  });

  it("watches nothing once every entry has been loaded", () => {
    state.hasNextPage = false;

    renderPanel();

    expect(callbacks).toHaveLength(0);
  });
});
