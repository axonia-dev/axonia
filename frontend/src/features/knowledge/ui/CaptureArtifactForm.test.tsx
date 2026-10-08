import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { setCurrentUserId } from "@/auth/current-user";
import { CaptureArtifactForm } from "@/features/knowledge/ui/CaptureArtifactForm";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(),
}));
vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useCaptureArtifact: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: () => ({ data: {} }),
}));

import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useCaptureArtifact } from "@/features/knowledge/api/use-artifacts";

const mockTypes = vi.mocked(useArtifactTypes);
const mockCapture = vi.mocked(useCaptureArtifact);
const mockAccess = vi.mocked(useManagementAccess);
const mockRosterByName = vi.mocked(useRosterByName);
const mockMemberSearch = vi.mocked(useMemberSearch);

// delay: null drops userEvent's per-event real-timer yields, so these
// interaction-heavy tests don't depend on regaining a timer slice between
// keystrokes — which is what made them exceed the 5s timeout under the
// CPU-starved full-suite coverage run.
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockTypes.mockReturnValue({
    data: [
      { id: 3, label: "Recording", icon: null, accentColor: null },
      { id: 4, label: "Slides", icon: null, accentColor: null },
    ],
  } as never);
  mockCapture.mockReturnValue({ mutate: vi.fn(), isError: false } as never);
  mockAccess.mockReturnValue({ credit: false, isLoading: false } as never);
  mockMemberSearch.mockReturnValue({ data: [], isFetching: false } as never);
  mockRosterByName.mockReturnValue({
    data: { pages: [] },
    hasNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
});

afterEach(() => {
  setCurrentUserId(undefined);
});

async function captureRecording() {
  await user.click(screen.getByRole("button", { name: /type/i }));
  await user.click(await screen.findByRole("option", { name: "Recording" }));
}

/** Types a name into the real Contributor picker, lets the debounce settle, and picks the
 *  given match from the resulting list. */
async function pickContributor(
  query: string,
  member: { userId: string; displayName: string },
) {
  mockMemberSearch.mockReturnValue({
    data: [member],
    isFetching: false,
  } as never);
  await user.type(screen.getByRole("combobox", { name: "Contributor" }), query);
  act(() => {
    vi.advanceTimersByTime(300);
  });
  await user.click(
    await screen.findByRole("option", { name: member.displayName }),
  );
}

/** Renders the capture form, submits a Recording titled "June dojo", picks the given
 *  contributor by name, and saves. */
async function saveWithContributor(
  query: string,
  member: { userId: string; displayName: string },
) {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  try {
    render(
      <CaptureArtifactForm
        slug="backend-guild"
        defaultTitle="June dojo"
        defaultUrl="https://example.com/rec"
        onCaptured={vi.fn()}
      />,
    );
    await captureRecording();
    await pickContributor(query, member);
    await user.click(screen.getByRole("button", { name: /^save$/i }));
  } finally {
    vi.useRealTimers();
  }
}

describe("CaptureArtifactForm", () => {
  it("disables submit until title, type, and url are provided", async () => {
    // given
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);
    const submit = screen.getByRole("button", { name: /^save$/i });
    expect(submit).toBeDisabled();

    // when
    await user.type(screen.getByLabelText(/title/i), "Hexagonal talk");
    await user.click(screen.getByRole("button", { name: /type/i }));
    await user.click(await screen.findByRole("option", { name: "Recording" }));
    await user.type(screen.getByLabelText(/link/i), "https://example.com/talk");

    // then
    expect(submit).toBeEnabled();
  }, 15_000);

  it("submits the capture input with entered tags and provenance", async () => {
    // given
    const mutate = vi.fn();
    mockCapture.mockReturnValue({ mutate, isError: false } as never);
    render(
      <CaptureArtifactForm
        slug="backend-guild"
        defaultTitle="June dojo"
        defaultUrl="https://example.com/rec"
        provenance={{ sessionId: 9 }}
        onCaptured={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: /type/i }));
    await user.click(await screen.findByRole("option", { name: "Recording" }));
    await user.type(
      screen.getByLabelText(/tags/i),
      "architecture{Enter}dojo{Enter}",
    );

    // when
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      title: "June dojo",
      typeId: 3,
      url: "https://example.com/rec",
      description: "",
      tags: ["architecture", "dojo"],
      provenance: { sessionId: 9 },
    });
  });

  it("shows a friendly error when capture fails", () => {
    // given
    mockCapture.mockReturnValue({
      mutate: vi.fn(),
      isError: true,
    } as never);

    // when
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it.each([
    [
      "the right closed",
      new ApiError(403, "detail"),
      "Crediting contributions is no longer open here.",
    ],
    [
      "the member gone",
      new ApiError(409, "detail", "CONTRIBUTOR_NOT_FOUND"),
      "That person is not a member of this community.",
    ],
  ])("reports a credit refused with %s", (_, error, expected) => {
    // given
    mockCapture.mockReturnValue({
      mutate: vi.fn(),
      isError: true,
      error,
      variables: { contributor: "bob" },
    } as never);

    // when
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(expected);
  });

  it("does not blame crediting for a refusal of a capture that credits nobody else", () => {
    // given
    mockCapture.mockReturnValue({
      mutate: vi.fn(),
      isError: true,
      error: new ApiError(403, "detail"),
      variables: { title: "Hexagonal talk" },
    } as never);

    // when
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("uses a custom submit label when provided", () => {
    render(
      <CaptureArtifactForm
        slug="backend-guild"
        submitLabel="Attach"
        onCaptured={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("button", { name: /^attach$/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^save$/i })).toBeNull();
  });

  it("renders a back control that fires onBack", async () => {
    const onBack = vi.fn();
    render(
      <CaptureArtifactForm
        slug="backend-guild"
        onBack={onBack}
        onCaptured={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: /back to search/i }));

    expect(onBack).toHaveBeenCalledOnce();
  });

  it("omits the back control when onBack is not given", () => {
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);

    expect(
      screen.queryByRole("button", { name: /back to search/i }),
    ).toBeNull();
  });

  it("offers no contributor field to a caller the community does not let credit", () => {
    render(<CaptureArtifactForm slug="backend-guild" onCaptured={vi.fn()} />);

    expect(screen.queryByRole("combobox", { name: "Contributor" })).toBeNull();
  });

  it("credits a member found by name through the real picker", async () => {
    mockAccess.mockReturnValue({ credit: true, isLoading: false } as never);
    setCurrentUserId("olive");
    const mutate = vi.fn();
    mockCapture.mockReturnValue({ mutate, isError: false } as never);

    await saveWithContributor("Bob", {
      userId: "bob",
      displayName: "Bob Martin",
    });

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({ contributor: "bob" });
  });

  it("omits the contributor field's own pick when it names the caller", async () => {
    mockAccess.mockReturnValue({ credit: true, isLoading: false } as never);
    setCurrentUserId("olive");
    const mutate = vi.fn();
    mockCapture.mockReturnValue({ mutate, isError: false } as never);

    await saveWithContributor("Olive", {
      userId: "olive",
      displayName: "Olive Organizer",
    });

    expect(mutate.mock.calls[0]?.[0]).not.toHaveProperty("contributor");
  });
});
