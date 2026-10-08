import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Community } from "@/features/community/domain/community";
import {
  CommunitySwitcher,
  SwitcherTile,
} from "@/features/community/ui/CommunitySwitcher";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: () => ({
    data: {
      content: [
        {
          id: 1,
          slug: "backend-guild",
          name: "Backend Guild",
          kindId: 1,
          memberCount: 120,
        },
        {
          id: 2,
          slug: "frontend-guild",
          name: "Frontend Guild",
          kindId: 1,
          memberCount: 98,
        },
      ],
    },
  }),
}));

const current = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  memberCount: 120,
  badgeUrl: "https://cdn/badge.png",
} as Community;

function renderSwitcher() {
  render(
    <MemoryRouter initialEntries={["/communities/backend-guild"]}>
      <Routes>
        <Route
          path="/communities/:slug"
          element={<CommunitySwitcher current={current} />}
        />
        <Route path="/communities" element={<div>directory</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CommunitySwitcher", () => {
  it("shows the current community's badge on the trigger tile", () => {
    renderSwitcher();
    expect(screen.getByRole("img", { name: "Backend Guild" })).toHaveAttribute(
      "src",
      "https://cdn/badge.png",
    );
  });

  it("navigates to a chosen community", async () => {
    renderSwitcher();
    await user.click(screen.getByRole("button", { name: /switch community/i }));
    await user.click(screen.getByRole("menuitem", { name: /frontend guild/i }));
    // navigating to /communities/frontend-guild re-renders the switcher route (no directory text)
    expect(screen.queryByText("directory")).toBeNull();
  });

  it("returns to the directory via browse-all", async () => {
    renderSwitcher();
    await user.click(screen.getByRole("button", { name: /switch community/i }));
    await user.click(
      screen.getByRole("menuitem", { name: /browse all communities/i }),
    );
    expect(screen.getByText("directory")).toBeInTheDocument();
  });
});

describe("SwitcherTile", () => {
  it("keeps the member count singular for exactly one member", () => {
    render(<SwitcherTile name="Solo Guild" hue="200" memberCount={1} />);
    expect(screen.getByText("1 member")).toBeInTheDocument();
  });

  it("pluralises the member count for more than one member", () => {
    render(<SwitcherTile name="Backend Guild" hue="200" memberCount={120} />);
    expect(screen.getByText("120 members")).toBeInTheDocument();
  });

  describe("the name's pop", () => {
    const POP = "motion-safe:animate-[count-pop_0.3s_ease]";
    const tileText = () => screen.getByText(/Guild/).parentElement;
    const endPop = () => fireEvent.animationEnd(screen.getByText(/Guild/));

    it("does not play when the tile appears", () => {
      // when
      render(<SwitcherTile name="Backend Guild" hue="200" memberCount={3} />);

      // then
      expect(tileText()).not.toHaveClass(POP);
    });

    it("plays when the community changes in place", () => {
      // given
      const { rerender } = render(
        <SwitcherTile name="Backend Guild" hue="200" memberCount={3} />,
      );

      // when
      rerender(
        <SwitcherTile name="Frontend Guild" hue="200" memberCount={3} />,
      );

      // then
      expect(tileText()).toHaveClass(POP);
    });

    it("plays when the community changes back", () => {
      // given
      const { rerender } = render(
        <SwitcherTile name="Backend Guild" hue="200" memberCount={3} />,
      );
      rerender(
        <SwitcherTile name="Frontend Guild" hue="200" memberCount={3} />,
      );

      // when
      rerender(<SwitcherTile name="Backend Guild" hue="200" memberCount={3} />);

      // then
      expect(tileText()).toHaveClass(POP);
    });

    it("does not play again when the rail expands after a switch", () => {
      // given
      const { rerender } = render(
        <SwitcherTile name="Backend Guild" hue="200" memberCount={3} />,
      );
      rerender(
        <SwitcherTile name="Frontend Guild" hue="200" memberCount={3} />,
      );
      endPop();
      rerender(
        <SwitcherTile
          name="Frontend Guild"
          hue="200"
          memberCount={3}
          collapsed
        />,
      );

      // when
      rerender(
        <SwitcherTile name="Frontend Guild" hue="200" memberCount={3} />,
      );

      // then
      expect(tileText()).not.toHaveClass(POP);
    });

    it("plays on the next switch after the last pop ended", () => {
      // given
      const { rerender } = render(
        <SwitcherTile name="Backend Guild" hue="200" memberCount={3} />,
      );
      rerender(
        <SwitcherTile name="Frontend Guild" hue="200" memberCount={3} />,
      );
      endPop();

      // when
      rerender(<SwitcherTile name="Backend Guild" hue="200" memberCount={3} />);

      // then
      expect(tileText()).toHaveClass(POP);
    });
  });
});
