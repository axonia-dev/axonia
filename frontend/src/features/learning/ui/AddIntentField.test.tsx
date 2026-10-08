import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { useAddIntent } from "@/features/learning/api/use-learning";
import type { WantedArea } from "@/features/learning/domain/learning";
import { AddIntentField } from "@/features/learning/ui/AddIntentField";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/features/learning/api/use-learning", () => ({
  useAddIntent: vi.fn(),
}));

const mockUseAddIntent = vi.mocked(useAddIntent);
const mockUseExpertiseAreas = vi.mocked(useExpertiseAreas);

const LIMIT_REFUSAL = new ApiError(409, "refused", "LEARNING_INTENT_LIMIT");

const WANTED: WantedArea[] = [
  { tag: "kafka", learners: 2, canBeAsked: 4 },
  { tag: "observability", learners: 3, canBeAsked: 0 },
];

let user: ReturnType<typeof userEvent.setup>;
let mutate: ReturnType<typeof vi.fn>;
let reset: ReturnType<typeof vi.fn>;

function adding(state: { isError: boolean; error: unknown }) {
  mockUseAddIntent.mockReturnValue({
    mutate,
    reset,
    isPending: false,
    ...state,
  } as never);
}

function renderField(
  props: Partial<{ slug: string; held: string[]; wanted: WantedArea[] }> = {},
) {
  return render(
    <AddIntentField slug="platform" held={[]} wanted={WANTED} {...props} />,
  );
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mutate = vi.fn();
  reset = vi.fn();
  adding({ isError: false, error: null });
  mockUseExpertiseAreas.mockReturnValue({
    data: [
      { canonicalTag: "kafka", variants: [], peopleCount: 4 },
      { canonicalTag: "kubernetes", variants: [], peopleCount: 1 },
    ],
  } as never);
});

describe("AddIntentField", () => {
  it("adds the typed area in the community", async () => {
    // given
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(mockUseAddIntent).toHaveBeenCalledWith("platform");
    expect(mutate.mock.calls[0]?.[0]).toBe("Rust");
  });

  it("clears the field once the area is stored", async () => {
    // given
    mutate.mockImplementation(
      (_tag: string, options?: { onSuccess?: () => void }) =>
        options?.onSuccess?.(),
    );
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(screen.getByRole("combobox")).toHaveValue("");
  });

  it("suggests the community's areas with who holds them, then areas only wanted with who wants them", async () => {
    // given
    renderField();

    // when
    await user.click(screen.getByRole("combobox"));

    // then
    expect(mockUseExpertiseAreas).toHaveBeenCalledWith("platform");
    const options = await screen.findAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "kafka4 people",
      "kubernetes1 person",
      "observability3 learners",
    ]);
  });

  it("suggests nothing the member already wants to learn", async () => {
    // given
    renderField({ held: ["kafka", "observability"] });

    // when
    await user.click(screen.getByRole("combobox"));

    // then
    const options = await screen.findAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "kubernetes1 person",
    ]);
  });

  it("says why an area was refused", async () => {
    // given
    adding({ isError: true, error: LIMIT_REFUSAL });
    renderField();
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(
      screen.getByText("At most 10 areas per community."),
    ).toBeInTheDocument();
  });

  it("keeps a refusal to the community it was sent to", async () => {
    // given
    adding({ isError: true, error: LIMIT_REFUSAL });
    const { rerender } = renderField();
    await user.type(screen.getByRole("combobox"), "Rust");
    await user.click(screen.getByRole("button", { name: "Add" }));

    // when
    rerender(<AddIntentField slug="data" held={[]} wanted={WANTED} />);

    // then
    expect(
      screen.queryByText("At most 10 areas per community."),
    ).not.toBeInTheDocument();
  });

  it("drops a refusal once the text changes", async () => {
    // given
    adding({ isError: true, error: LIMIT_REFUSAL });
    renderField();

    // when
    await user.type(screen.getByRole("combobox"), "x");

    // then
    expect(reset).toHaveBeenCalled();
  });

  it("leaves an add without a refusal alone when the text changes", async () => {
    // given
    renderField();

    // when
    await user.type(screen.getByRole("combobox"), "x");

    // then
    expect(reset).not.toHaveBeenCalled();
  });
});
