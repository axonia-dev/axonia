import type { UseQueryResult } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useAddSpeaker } from "@/features/events/api/use-topic-speakers";
import { useSpeakerSuggestions } from "@/features/expertise/api/use-speaker-suggestions";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { SpeakerSuggestions } from "@/features/expertise/ui/SpeakerSuggestions";
import { ApiError } from "@/lib/api-client";

type SuggestionsResult = UseQueryResult<Practitioner[], Error>;

vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(),
}));

vi.mock("@/features/events/api/use-topic-speakers", () => ({
  useAddSpeaker: vi.fn(),
}));

vi.mock("@/features/expertise/api/use-speaker-suggestions", () => ({
  useSpeakerSuggestions: vi.fn(),
}));

const mockUseIsOrganizer = vi.mocked(useIsOrganizer);
const mockUseSpeakerSuggestions = vi.mocked(useSpeakerSuggestions);
const mockUseAddSpeaker = vi.mocked(useAddSpeaker);
let addSpeaker: ReturnType<typeof vi.fn>;

const alice: Practitioner = {
  userId: "alice",
  displayName: "Alice Dupont",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER",
  evidence: {
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
};

function renderComponent(slug = "my-guild", tags: string[] = ["ddd"]) {
  return render(<SpeakerSuggestions slug={slug} topicId={5} tags={tags} />);
}

describe("SpeakerSuggestions", () => {
  beforeEach(() => {
    mockUseSpeakerSuggestions.mockReturnValue({
      data: [alice],
    } as unknown as SuggestionsResult);
    mockUseIsOrganizer.mockReturnValue(true);
    addSpeaker = vi.fn();
    mockUseAddSpeaker.mockReturnValue({
      mutate: addSpeaker,
      isPending: false,
      isError: false,
      error: null,
    } as never);
  });

  it("offers each suggestion as a button that adds that person as a speaker", async () => {
    // given
    renderComponent();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Add Alice Dupont as speaker" }),
    );

    // then
    expect(mockUseAddSpeaker).toHaveBeenCalledWith("my-guild", 5);
    expect(addSpeaker.mock.calls[0]?.[0]).toBe("alice");
  });

  it("says so when the suggested person could not be matched", async () => {
    // given
    addSpeaker.mockImplementation(
      (
        _speaker: string,
        opts: { onSuccess: (result: { assigned: boolean }) => void },
      ) => opts.onSuccess({ assigned: false }),
    );
    renderComponent();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Add Alice Dupont as speaker" }),
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "That person could not be matched.",
    );
  });

  it("holds every chip while a speaker is being added", () => {
    // given
    mockUseAddSpeaker.mockReturnValue({
      mutate: addSpeaker,
      isPending: true,
      isError: false,
      error: null,
    } as never);

    // when
    renderComponent();

    // then
    expect(
      screen.getByRole("button", { name: "Add Alice Dupont as speaker" }),
    ).toBeDisabled();
  });

  it("explains a refused addition", () => {
    // given
    mockUseAddSpeaker.mockReturnValue({
      mutate: addSpeaker,
      isPending: false,
      isError: true,
      error: new ApiError(409, "x", "NOT_PROPOSED"),
    } as never);

    // when
    renderComponent();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This topic is no longer on the backlog.",
    );
  });

  it("renders suggestions when organizer, tags present, and data returned", () => {
    renderComponent();

    expect(screen.getByText("Suggested speakers:")).toBeInTheDocument();
    expect(screen.getByText("Alice Dupont")).toBeInTheDocument();
  });

  it("renders nothing when not an organizer", () => {
    mockUseIsOrganizer.mockReturnValue(false);
    const { container } = renderComponent();

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when tags array is empty", () => {
    const { container } = renderComponent("my-guild", []);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when no suggestions returned", () => {
    mockUseSpeakerSuggestions.mockReturnValue({
      data: [],
    } as unknown as SuggestionsResult);

    const { container } = renderComponent();

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when suggestions data is undefined", () => {
    mockUseSpeakerSuggestions.mockReturnValue({
      data: undefined,
    } as unknown as SuggestionsResult);

    const { container } = renderComponent();

    expect(container).toBeEmptyDOMElement();
  });
});
