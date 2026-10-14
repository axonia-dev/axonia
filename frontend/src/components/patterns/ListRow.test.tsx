import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ListRow } from "@/components/patterns/ListRow";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("ListRow", () => {
  it("renders the action beside the link, not inside it", () => {
    // given
    render(
      <MemoryRouter>
        <ListRow
          href="/communities/a"
          title="A"
          action={<button type="button">Join</button>}
        />
      </MemoryRouter>,
    );

    // when
    const link = screen.getByRole("link", { name: /A/ });
    const join = screen.getByRole("button", { name: "Join" });

    // then
    expect(link.contains(join)).toBe(false);
  });

  it("links to its href with the title as the accessible name", () => {
    // when
    render(
      <MemoryRouter>
        <ListRow
          href="/communities/backend-guild"
          title="Backend Guild"
          meta="42 members"
          accent="#de3b78"
        />
      </MemoryRouter>,
    );

    // then
    expect(
      screen.getByRole("link", { name: /backend guild/i }),
    ).toHaveAttribute("href", "/communities/backend-guild");
    expect(screen.getByText("42 members")).toBeInTheDocument();
  });

  it("renders a button and fires onPress when activated", async () => {
    // given
    const onPress = vi.fn();
    render(
      <ListRow title="Alice Anderson" meta="Active now" onPress={onPress} />,
    );

    // when
    await user.click(screen.getByRole("button", { name: /alice anderson/i }));

    // then
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
