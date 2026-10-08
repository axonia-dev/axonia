import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Pagination } from "@/components/patterns/Pagination";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("Pagination", () => {
  it("shows the human-readable page position", () => {
    // when
    render(<Pagination page={0} totalPages={3} onPageChange={vi.fn()} />);

    // then
    expect(screen.getByText(/page 1 of 3/i)).toBeInTheDocument();
  });

  it("disables Previous on the first page", () => {
    // when
    render(<Pagination page={0} totalPages={3} onPageChange={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: /previous/i })).toBeDisabled();
  });

  it("advances to the next page", async () => {
    // given
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);

    // when
    await user.click(screen.getByRole("button", { name: /next/i }));

    // then
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});
