import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedItem } from "@/features/bookmarks/domain/saved";
import {
  sessionView,
  view,
} from "@/features/bookmarks/domain/saved-filter-fixture";
import { SavedHelpfulPrompt } from "@/features/bookmarks/ui/SavedHelpfulPrompt";

let items: SavedItem[] | undefined;

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useMySaved: () => ({ data: items }),
  useMarkHelpful: () => ({ mutate: vi.fn(), isPending: false }),
}));

beforeEach(() => {
  items = [];
});

describe("SavedHelpfulPrompt", () => {
  it("reads the saved artifact's helpful answer", () => {
    items = [sessionView({ id: 7 }), view({ id: 7, helped: true })];

    render(<SavedHelpfulPrompt artifactId={7} />);

    expect(screen.getByText("Marked helpful")).toBeInTheDocument();
  });

  it("asks when the artifact is not in the saved list yet", () => {
    items = undefined;

    render(<SavedHelpfulPrompt artifactId={7} />);

    expect(screen.getByText("Was this helpful?")).toBeInTheDocument();
  });
});
