import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LibraryHeader } from "@/features/knowledge/ui/LibraryHeader";

vi.mock("@/features/knowledge/api/use-library-insights", () => ({
  useLibraryStats: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/CaptureArtifactButton", () => ({
  CaptureArtifactButton: () => <button type="button">Capture artifact</button>,
}));

import { useLibraryStats } from "@/features/knowledge/api/use-library-insights";

const mockStats = vi.mocked(useLibraryStats);

beforeEach(() => vi.clearAllMocks());

describe("LibraryHeader", () => {
  it("renders the vitality line from the stats", () => {
    // given
    mockStats.mockReturnValue({
      data: { total: 12, sessionCount: 3, capturedThisMonth: 2 },
    } as never);

    // when
    render(<LibraryHeader slug="eng" />);

    // then
    expect(screen.getByText(/artifacts? from/)).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText(/this month/)).toBeInTheDocument();
  });

  it("shows a loading line before the stats arrive", () => {
    // given
    mockStats.mockReturnValue({ data: undefined } as never);

    // when
    render(<LibraryHeader slug="eng" />);

    // then
    expect(screen.getByText(/taking stock/i)).toBeInTheDocument();
  });

  it("shows an empty-shelf line when there are no artifacts", () => {
    // given
    mockStats.mockReturnValue({
      data: { total: 0, sessionCount: 0, capturedThisMonth: 0 },
    } as never);

    // when
    render(<LibraryHeader slug="eng" />);

    // then
    expect(screen.getByText(/empty shelf/i)).toBeInTheDocument();
  });
});
