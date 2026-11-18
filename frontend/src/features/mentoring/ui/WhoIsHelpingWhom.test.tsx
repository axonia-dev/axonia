import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { shownPairingOf } from "@/features/mentoring/test-data";
import { WhoIsHelpingWhom } from "@/features/mentoring/ui/WhoIsHelpingWhom";
import { useNow } from "@/lib/use-wake-at";

vi.mock("@/lib/use-wake-at", () => ({ useNow: vi.fn() }));

beforeEach(() => {
  vi.mocked(useNow).mockReturnValue(new Date("2026-11-18T09:00:00Z"));
});

describe("WhoIsHelpingWhom", () => {
  it("shows each pairing as mentor, since when, then mentee", () => {
    // when
    render(<WhoIsHelpingWhom pairings={[shownPairingOf()]} />);

    // then
    expect(
      screen.getByRole("heading", { name: "Who’s helping whom" }),
    ).toBeInTheDocument();
    const [row] = screen.getAllByRole("listitem");
    expect(row).toHaveTextContent(
      /Mentor.*Ana Keller.*since Oct.*Mentee.*Dan Ilić/,
    );
    expect(within(row!).getByText("Mentor")).toHaveClass("text-mentor");
    expect(within(row!).getByText("Mentee")).toHaveClass("text-mentee");
  });

  it("adds the year to a pairing begun in another year", () => {
    // when
    render(
      <WhoIsHelpingWhom
        pairings={[
          shownPairingOf({ startedOn: "2025-12-31" }),
          shownPairingOf({ id: 22, startedOn: "2026-01-01" }),
        ]}
      />,
    );

    // then
    expect(screen.getByText("since Dec 2025")).toBeInTheDocument();
    expect(screen.getByText("since Jan")).toBeInTheDocument();
  });

  it("keeps the server's order", () => {
    // when
    render(
      <WhoIsHelpingWhom
        pairings={[
          shownPairingOf(),
          shownPairingOf({
            id: 22,
            mentor: {
              userId: "u-6",
              displayName: "Carl Brun",
              avatarUrl: null,
            },
          }),
        ]}
      />,
    );

    // then
    const rows = screen.getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("Ana Keller");
    expect(rows[1]).toHaveTextContent("Carl Brun");
  });

  it("renders nothing while no pairing shows", () => {
    // when
    const { container } = render(<WhoIsHelpingWhom pairings={[]} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
