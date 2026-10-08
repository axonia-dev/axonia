import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { OpenToQuestionsMarker } from "@/features/expertise/ui/OpenToQuestionsMarker";

describe("OpenToQuestionsMarker", () => {
  it("names the declaration and removes it", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    const onRemove = vi.fn();
    render(<OpenToQuestionsMarker tag="kafka" onRemove={onRemove} />);

    // when
    await user.click(
      screen.getByRole("button", {
        name: "Stop being open to questions on kafka",
      }),
    );

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(onRemove).toHaveBeenCalledWith("kafka");
  });

  it("offers no removal where none is possible", () => {
    // when
    render(<OpenToQuestionsMarker tag="kafka" />);

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("sends no second removal while one is in flight", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    const onRemove = vi.fn();
    render(
      <OpenToQuestionsMarker tag="kafka" onRemove={onRemove} isRemoving />,
    );

    // when
    await user.click(
      screen.getByRole("button", {
        name: "Stop being open to questions on kafka",
      }),
    );

    // then
    expect(onRemove).not.toHaveBeenCalled();
  });
});
