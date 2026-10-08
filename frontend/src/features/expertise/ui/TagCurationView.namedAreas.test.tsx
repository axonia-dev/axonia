import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCurationSuggestions,
  useCurationVocabulary,
} from "@/features/expertise/api/use-curation";
import { TagCurationView } from "@/features/expertise/ui/TagCurationView";

vi.mock("@/features/expertise/api/use-curation", () => ({
  useCurationSuggestions: vi.fn(),
  useCurationVocabulary: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-tag-merge", () => ({
  useTagMerge: () => ({ mergeInto: vi.fn(), splitOff: vi.fn() }),
}));
vi.mock("@/features/expertise/api/use-tag-dismissal", () => ({
  useTagDismissal: () => ({ dismissPair: vi.fn() }),
}));
vi.mock("@/features/expertise/api/use-tag-lifecycle", () => ({
  useTagLifecycle: () => ({ renameTag: vi.fn(), deleteTag: vi.fn() }),
  useTagUsage: () => ({ isSuccess: false, isError: false }),
}));
vi.mock("@/features/expertise/ui/NamedAreaPanel", () => ({
  NamedAreaPanel: ({
    slug,
    vocabulary,
  }: {
    slug: string;
    vocabulary: string[];
  }) => <p>{`named areas of ${slug}: ${vocabulary.join(" ")}`}</p>,
}));

beforeEach(() => {
  vi.mocked(useCurationSuggestions).mockReturnValue({
    data: [],
    isLoading: false,
  } as never);
  vi.mocked(useCurationVocabulary).mockReturnValue({
    data: [
      { canonicalTag: "kafka", variants: [], peopleCount: 4 },
      {
        canonicalTag: "domain-driven-design",
        variants: ["ddd"],
        peopleCount: 5,
      },
    ],
    isLoading: false,
  } as never);
});

describe("TagCurationView named areas", () => {
  it("offers the named areas over the community's canonical tags", () => {
    // when
    render(<TagCurationView slug="eng" />);

    // then
    expect(
      screen.getByText("named areas of eng: kafka domain-driven-design"),
    ).toBeInTheDocument();
  });
});
