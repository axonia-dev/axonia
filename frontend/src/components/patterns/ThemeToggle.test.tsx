import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { ThemeToggle } from "@/components/patterns/ThemeToggle";
import { ThemeProvider } from "@/theme/ThemeProvider";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("ThemeToggle", () => {
  it("toggles the theme via an accessible control", async () => {
    // given
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    );
    const before = screen.getByRole("button").getAttribute("aria-label");

    // when
    await user.click(screen.getByRole("button"));

    // then
    expect(screen.getByRole("button").getAttribute("aria-label")).not.toBe(
      before,
    );
  });
});
