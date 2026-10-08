import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useStartSeries,
  useUpdateSeries,
} from "@/features/events/api/use-series";
import { SeriesForm } from "@/features/events/ui/SeriesForm";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: [] })),
}));
vi.mock("@/features/events/api/use-series", () => ({
  useStartSeries: vi.fn(),
  useUpdateSeries: vi.fn(),
}));
vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useStartSeries).mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
  vi.mocked(useUpdateSeries).mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
});

describe("SeriesForm: the default structure before the section types are in", () => {
  it("says they are loading, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue({
      data: undefined,
      errorUpdateCount: 0,
    } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.click(screen.getByRole("button", { name: /Default structure/ }));

    // then
    expect(screen.getByText("Loading…")).toBeVisible();
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });

  it("says they could not load, rather than that none exist", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.click(screen.getByRole("button", { name: /Default structure/ }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the section types.",
    );
    expect(screen.queryByText(/No section types yet/)).not.toBeInTheDocument();
  });

  it("hands the focus to the structure editor once Try again brings them", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const { rerender } = render(
      <SeriesForm slug="backend-guild" onSaved={vi.fn()} />,
    );
    await user.click(screen.getByRole("button", { name: /Default structure/ }));
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    vi.mocked(useSectionTypes).mockReturnValue({
      data: [
        {
          id: 1,
          label: "Talk",
          expectsPresenter: true,
          icon: null,
          accentColor: null,
        },
      ],
      errorUpdateCount: 1,
    } as never);
    rerender(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    // then
    expect(screen.getByRole("group", { name: "Structure" })).toHaveFocus();
  });

  it("leaves the focus in the Title field when they answer while it is typed in", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    const { rerender } = render(
      <SeriesForm slug="backend-guild" onSaved={vi.fn()} />,
    );
    await user.click(screen.getByRole("button", { name: /Default structure/ }));
    await user.type(screen.getByLabelText(/^Title/), "Sync");

    // when
    vi.mocked(useSectionTypes).mockReturnValue({
      data: [],
      errorUpdateCount: 1,
    } as never);
    rerender(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    // then
    expect(screen.getByLabelText(/^Title/)).toHaveFocus();
  });

  it("leaves the focus where it is when they answer with no Try again pressed", async () => {
    // given
    vi.mocked(useSectionTypes).mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    const { rerender } = render(
      <SeriesForm slug="backend-guild" onSaved={vi.fn()} />,
    );
    await user.click(screen.getByRole("button", { name: /Default structure/ }));
    (document.activeElement as HTMLElement).blur();

    // when
    vi.mocked(useSectionTypes).mockReturnValue({
      data: [],
      errorUpdateCount: 1,
    } as never);
    rerender(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    // then
    expect(screen.getByRole("group", { name: "Structure" })).not.toHaveFocus();
  });
});
