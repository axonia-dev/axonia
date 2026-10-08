import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useCreateOneOff,
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import { OneOffSessionForm } from "@/features/events/ui/OneOffSessionForm";
import { SessionActions } from "@/features/events/ui/SessionActions";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useCreateOneOff: vi.fn(),
  useRescheduleSession: vi.fn(),
  useSkipOccurrence: vi.fn(),
}));
vi.mock("@/features/events/api/use-session-structure", () => ({
  useAddSection: () => ({ mutate: vi.fn() }),
  useUpdateSection: () => ({ mutate: vi.fn() }),
  useRemoveSection: () => ({ mutate: vi.fn() }),
  useReorderSections: () => ({ mutate: vi.fn() }),
}));

const LOADING = { data: undefined, errorUpdateCount: 0 };
const FAILED = {
  data: undefined,
  errorUpdateCount: 1,
  isFetching: false,
  refetch: vi.fn(),
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useCreateOneOff).mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
  vi.mocked(useRescheduleSession).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    reset: vi.fn(),
  } as never);
  vi.mocked(useSkipOccurrence).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

async function openOneOffStructure() {
  render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: /Structure/ }));
}

async function openEditSession() {
  render(
    <SessionActions
      slug="g"
      session={aScheduledSession()}
      canEditStructure={true}
      canManage={false}
    />,
  );
  await user.click(screen.getByRole("button", { name: /session actions/i }));
  await user.click(screen.getByRole("menuitem", { name: /^edit$/i }));
}

describe("the one-off session form before the section types are in", () => {
  it("says they are loading, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue(LOADING as never);

    // when
    await openOneOffStructure();

    // then
    expect(screen.getByText("Loading…")).toBeVisible();
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });

  it("says they could not load, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue(FAILED as never);

    // when
    await openOneOffStructure();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the section types.",
    );
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });
});

describe("the Edit session dialog before the section types are in", () => {
  it("says they are loading, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue(LOADING as never);

    // when
    await openEditSession();

    // then
    expect(screen.getByText("Loading…")).toBeVisible();
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });

  it("says they could not load, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue(FAILED as never);

    // when
    await openEditSession();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the section types.",
    );
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });
});
