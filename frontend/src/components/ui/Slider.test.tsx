import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Slider } from "@/components/ui/Slider";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("Slider", () => {
  it("shows the formatted value and reports keyboard steps", async () => {
    const onChange = vi.fn();
    render(
      <Slider
        label="Minutes"
        value={15}
        onChange={onChange}
        minValue={5}
        maxValue={60}
        step={5}
        formatValue={(v) => `${v} min`}
      />,
    );

    expect(screen.getByText("15 min")).toBeInTheDocument();

    const thumb = screen.getByRole("slider");
    thumb.focus();
    await user.keyboard("{ArrowRight}");

    expect(onChange).toHaveBeenCalledWith(20);
  });

  it("renders a helper description", () => {
    render(
      <Slider
        label="Minutes"
        value={15}
        onChange={vi.fn()}
        minValue={5}
        maxValue={60}
        step={5}
        description="45 min left in this section"
      />,
    );

    expect(screen.getByText("45 min left in this section")).toBeInTheDocument();
  });
});
