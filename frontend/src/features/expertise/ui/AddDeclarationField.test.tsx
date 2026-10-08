import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useDeclareArea } from "@/features/expertise/api/use-declarations";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { AddDeclarationField } from "@/features/expertise/ui/AddDeclarationField";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/expertise/api/use-declarations", () => ({
  useDeclareArea: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));

const mockUseDeclareArea = vi.mocked(useDeclareArea);
const mockUseExpertiseAreas = vi.mocked(useExpertiseAreas);

let user: ReturnType<typeof userEvent.setup>;
let mutate: ReturnType<typeof vi.fn>;
let reset: ReturnType<typeof vi.fn>;

const LIMIT_REFUSAL = new ApiError(409, "refused", "DECLARATION_LIMIT");

function declaring(state: {
  isError: boolean;
  error: unknown;
  isPending?: boolean;
}) {
  mockUseDeclareArea.mockReturnValue({
    mutate,
    reset,
    isPending: false,
    ...state,
  } as never);
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mutate = vi.fn();
  reset = vi.fn();
  declaring({ isError: false, error: null });
  mockUseExpertiseAreas.mockReturnValue({
    data: [
      { canonicalTag: "kafka", variants: [], peopleCount: 4 },
      { canonicalTag: "terraform", variants: [], peopleCount: 1 },
    ],
  } as never);
});

describe("AddDeclarationField", () => {
  it("declares the typed area in the community", async () => {
    // given
    render(<AddDeclarationField slug="platform" />);
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(mockUseDeclareArea).toHaveBeenCalledWith("platform");
    expect(mutate.mock.calls[0]?.[0]).toBe("Rust");
  });

  it("clears the field once the declaration is stored", async () => {
    // given
    mutate.mockImplementation(
      (_tag: string, options?: { onSuccess?: () => void }) =>
        options?.onSuccess?.(),
    );
    render(<AddDeclarationField slug="platform" />);
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(screen.getByRole("combobox")).toHaveValue("");
  });

  it("suggests the community's existing areas with how many people hold them", async () => {
    // given
    render(<AddDeclarationField slug="platform" />);

    // when
    await user.type(screen.getByRole("combobox"), "ka");

    // then
    expect(mockUseExpertiseAreas).toHaveBeenCalledWith("platform");
    expect(
      await screen.findByRole("option", { name: /kafka/ }),
    ).toHaveTextContent("4 people");
  });

  it("says why a declaration was refused", async () => {
    // given
    declaring({ isError: true, error: LIMIT_REFUSAL });
    render(<AddDeclarationField slug="platform" />);
    await user.type(screen.getByRole("combobox"), "Rust");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(
      screen.getByText("At most 20 areas per community."),
    ).toBeInTheDocument();
  });

  it("keeps a refusal to the community it was sent to", async () => {
    // given
    declaring({ isError: true, error: LIMIT_REFUSAL });
    const { rerender } = render(<AddDeclarationField slug="platform" />);
    await user.type(screen.getByRole("combobox"), "Rust");
    await user.click(screen.getByRole("button", { name: "Add" }));

    // when
    rerender(<AddDeclarationField slug="data" />);

    // then
    expect(
      screen.queryByText("At most 20 areas per community."),
    ).not.toBeInTheDocument();
  });

  it("drops a refusal once the text changes", async () => {
    // given
    declaring({ isError: true, error: LIMIT_REFUSAL });
    render(<AddDeclarationField slug="platform" />);

    // when
    await user.type(screen.getByRole("combobox"), "x");

    // then
    expect(reset).toHaveBeenCalled();
  });

  it("leaves a declaration without a refusal alone when the text changes", async () => {
    // given
    render(<AddDeclarationField slug="platform" />);

    // when
    await user.type(screen.getByRole("combobox"), "x");

    // then
    expect(reset).not.toHaveBeenCalled();
  });

  it("sends nothing more while a declaration is in flight", async () => {
    // given
    declaring({ isError: false, error: null, isPending: true });
    render(<AddDeclarationField slug="platform" />);
    await user.type(screen.getByRole("combobox"), "Rust{Enter}");

    // when
    await user.click(screen.getByRole("button", { name: "Add" }));

    // then
    expect(mutate).not.toHaveBeenCalled();
  });
});
