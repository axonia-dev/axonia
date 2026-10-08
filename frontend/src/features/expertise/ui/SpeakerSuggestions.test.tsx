import type { UseQueryResult } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useSpeakerSuggestions } from "@/features/expertise/api/use-speaker-suggestions";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { SpeakerSuggestions } from "@/features/expertise/ui/SpeakerSuggestions";

type SuggestionsResult = UseQueryResult<Practitioner[], Error>;

vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(),
}));

vi.mock("@/features/expertise/api/use-speaker-suggestions", () => ({
  useSpeakerSuggestions: vi.fn(),
}));

const mockUseIsOrganizer = vi.mocked(useIsOrganizer);
const mockUseSpeakerSuggestions = vi.mocked(useSpeakerSuggestions);

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
  return render(<SpeakerSuggestions slug={slug} tags={tags} />);
}

describe("SpeakerSuggestions", () => {
  beforeEach(() => {
    mockUseSpeakerSuggestions.mockReturnValue({
      data: [alice],
    } as unknown as SuggestionsResult);
    mockUseIsOrganizer.mockReturnValue(true);
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
