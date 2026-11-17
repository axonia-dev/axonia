import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  journalOf,
  noteOf,
  pairingOf,
  topicOf,
} from "@/features/mentoring/test-data";
import { Finale } from "@/features/mentoring/ui/finale/Finale";

const ended = pairingOf({
  startedAt: "2026-09-02T09:00:00Z",
  endedAt: "2026-11-16T09:00:00Z",
});

function show(overrides: Partial<Journal> = {}) {
  const onPostcard = vi.fn();
  const { container } = render(
    <MemoryRouter>
      <Finale
        journal={journalOf({ pairing: ended, ...overrides })}
        onPostcard={onPostcard}
      />
    </MemoryRouter>,
  );
  return { onPostcard, container };
}

describe("Finale", () => {
  it("shows the finale only once ended", () => {
    // when
    const { container } = show({ pairing: pairingOf() });

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("plants the last flag with what the trail held", () => {
    // when
    show({
      topics: [
        topicOf({ id: 1, state: "DONE" }),
        topicOf({ id: 2, state: "TODO" }),
      ],
      notes: [
        noteOf({ id: 1, meetingOn: "2026-10-01" }),
        noteOf({ id: 2, meetingOn: "2026-10-01", mine: false }),
        noteOf({ id: 3, meetingOn: "2026-11-05" }),
      ],
    });

    // then
    expect(
      screen.getByRole("heading", { name: "11 weeks on the trail" }),
    ).toBeInTheDocument();
    expect(screen.getByText("2 Sep 2026 to 16 Nov 2026")).toBeInTheDocument();
    expect(screen.getByRole("list")).toHaveTextContent(
      "1 topic done2 meetings3 notes",
    );
  });

  it("Later keeps the postcard waiting at the finale", async () => {
    // given
    const { onPostcard } = show({ myWords: null, canWriteWords: true });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Write a postcard" }),
    );

    // then
    expect(onPostcard).toHaveBeenCalled();
  });

  it("says words are closed to a mentee once the year has passed", () => {
    // when
    show({ myWords: null, canWriteWords: false });

    // then
    expect(
      screen.getByText("Words are closed for this pairing."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("the mentor reads no words in the journal", () => {
    // when
    show({
      pairing: {
        ...ended,
        myRole: "MENTOR",
        partnerName: "Noah",
        partnerId: "u-2",
      },
      wordsWritten: true,
      canWriteWords: false,
    });

    // then
    expect(
      screen.getByRole("link", { name: "Words from Noah wait on My impact" }),
    ).toHaveAttribute("href", "/me/impact");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("names the partner whole when no given name is known", async () => {
    // given
    const { onPostcard } = show({
      pairing: pairingOf({ partnerName: "Rochat, Aurélia-NORTHWIND" }),
      myWords: "Thank you.",
      canWriteWords: true,
    });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Open the postcard" }),
    );

    // then
    expect(
      screen.getByText("A postcard went to Rochat, Aurélia-NORTHWIND."),
    ).toBeInTheDocument();
    expect(onPostcard).toHaveBeenCalled();
  });

  it("greets the partner by their given name when one is known", async () => {
    // given
    show({
      pairing: pairingOf({
        partnerName: "Rochat, Aurélia-NORTHWIND",
        partnerGivenName: "Aurélia",
      }),
      myWords: "Thank you.",
      canWriteWords: true,
    });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Open the postcard" }),
    );

    // then
    expect(screen.getByText("A postcard went to Aurélia.")).toBeInTheDocument();
  });

  it("no ask for words anywhere", () => {
    // when
    show({
      pairing: { ...ended, myRole: "MENTOR" },
      wordsWritten: false,
      canWriteWords: false,
    });

    // then
    expect(screen.queryByText(/words|postcard/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("an ongoing pairing with words keeps them editable", async () => {
    // given
    const { onPostcard } = show({
      pairing: pairingOf(),
      myWords: "Thank you.",
      canWriteWords: true,
    });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Open the postcard" }),
    );

    // then
    expect(screen.getByText("A postcard went to Ada.")).toBeInTheDocument();
    expect(onPostcard).toHaveBeenCalled();
  });
});
