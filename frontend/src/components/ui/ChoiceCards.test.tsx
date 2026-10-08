import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ChoiceCards } from "./ChoiceCards";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const OPTIONS = [
  { id: "PUBLIC", label: "Public", description: "Anyone can find and join" },
  { id: "PRIVATE", label: "Private", description: "Hidden, invite only" },
] as const;

describe("ChoiceCards", () => {
  it("selects an option", async () => {
    // given
    const onChange = vi.fn();
    render(
      <ChoiceCards
        label="Visibility"
        value="PUBLIC"
        options={OPTIONS}
        onChange={onChange}
      />,
    );

    // when
    await user.click(screen.getByRole("radio", { name: /private/i }));

    // then
    expect(onChange).toHaveBeenCalledWith("PRIVATE");
  });

  // The description is the whole of what a card says beyond its label, so it has to be visibly
  // subordinate to the label rather than a second line of it.
  it("draws the description apart from the label", () => {
    render(
      <ChoiceCards
        label="Visibility"
        value="PUBLIC"
        options={OPTIONS}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Anyone can find and join")).toHaveClass(
      "text-muted-foreground",
    );
  });
});
