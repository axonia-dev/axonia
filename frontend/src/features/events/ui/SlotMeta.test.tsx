import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { SlotMeta } from "@/features/events/ui/SlotMeta";

const slotFixture = {
  id: 1,
  sectionId: 2,
  position: 0,
  presenterUserIds: [] as string[],
  topicId: null as number | null,
  title: null as string | null,
  notes: null as string | null,
  durationMinutes: 20,
  tags: [] as string[],
};

const longNotes =
  "News and guidance on SonarCloud: how to log in via GitHub, navigate organisational " +
  "settings, and use rules, quality profiles and quality gates to enforce code standards.";

/** jsdom lays nothing out: every box is 0x0. Dictate what the clamp did to the description. */
function stubClamp(scrollHeight: number, clientHeight: number) {
  Object.defineProperty(HTMLParagraphElement.prototype, "scrollHeight", {
    configurable: true,
    value: scrollHeight,
  });
  Object.defineProperty(HTMLParagraphElement.prototype, "clientHeight", {
    configurable: true,
    value: clientHeight,
  });
}

afterEach(() => {
  Reflect.deleteProperty(HTMLParagraphElement.prototype, "scrollHeight");
  Reflect.deleteProperty(HTMLParagraphElement.prototype, "clientHeight");
});

describe("SlotMeta", () => {
  it("renders the description", () => {
    render(<SlotMeta slot={{ ...slotFixture, notes: "An abstract" }} />);
    expect(screen.getByText("An abstract")).toBeInTheDocument();
  });

  // Not merely "no text": no element either. An empty box would still be a flex child of the
  // slot card, and would draw the card's row gap on both of its sides.
  it("renders nothing for an open, note-less, topic-less, tag-less slot", () => {
    const { container } = render(<SlotMeta slot={slotFixture} />);
    expect(container.firstChild).toBeNull();
  });

  it("shows the backlog tag when topicId is set", () => {
    render(<SlotMeta slot={{ ...slotFixture, topicId: 42 }} />);
    expect(screen.getByText(/from backlog/i)).toBeInTheDocument();
  });

  it("shows a slot's topics", () => {
    render(
      <SlotMeta slot={{ ...slotFixture, tags: ["kafka", "streaming"] }} />,
    );
    expect(screen.getByText("#kafka")).toBeInTheDocument();
    expect(screen.getByText("#streaming")).toBeInTheDocument();
  });

  it("offers no way to expand a description that already fits", () => {
    stubClamp(40, 40);
    render(<SlotMeta slot={{ ...slotFixture, notes: "An abstract" }} />);
    expect(
      screen.queryByRole("button", { name: /show more/i }),
    ).not.toBeInTheDocument();
  });

  it("offers to show more of a description the clamp cut off", () => {
    stubClamp(72, 40);
    render(<SlotMeta slot={{ ...slotFixture, notes: longNotes }} />);
    expect(screen.getByRole("button", { name: /show more/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });

  it("expands the description in place, and collapses it again", async () => {
    const user = userEvent.setup({ delay: null });
    stubClamp(72, 40);
    render(<SlotMeta slot={{ ...slotFixture, notes: longNotes }} />);

    await user.click(screen.getByRole("button", { name: /show more/i }));

    expect(screen.getByText(longNotes)).not.toHaveClass("line-clamp-2");
    expect(screen.getByRole("button", { name: /show less/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );

    await user.click(screen.getByRole("button", { name: /show less/i }));

    expect(screen.getByText(longNotes)).toHaveClass("line-clamp-2");
    expect(screen.getByRole("button", { name: /show more/i })).toBeVisible();
  });

  // A slot pulled from the backlog carries no tags of its own, so the row the control lives in
  // exists for the control alone. It still has to be there.
  it("offers to show more on a slot with no subject of its own", () => {
    stubClamp(72, 40);
    render(<SlotMeta slot={{ ...slotFixture, notes: longNotes }} />);
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /show more/i })).toBeVisible();
  });

  it("names the description it governs", () => {
    stubClamp(72, 40);
    render(<SlotMeta slot={{ ...slotFixture, notes: longNotes }} />);
    const toggle = screen.getByRole("button", { name: /show more/i });
    expect(screen.getByText(longNotes)).toHaveAttribute(
      "id",
      toggle.getAttribute("aria-controls"),
    );
  });
});
