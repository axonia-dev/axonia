import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useRescheduleSession,
  useSkipOccurrence,
} from "@/features/events/api/use-sessions";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SessionActions } from "@/features/events/ui/SessionActions";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: () => ({ data: [] }),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useRescheduleSession: vi.fn(),
  useSkipOccurrence: vi.fn(),
}));
vi.mock("@/features/events/ui/SessionStructureEditor", () => ({
  SessionStructureEditor: () => <div data-testid="structure-editor" />,
}));

const mockUseReschedule = vi.mocked(useRescheduleSession);
const mockUseSkip = vi.mocked(useSkipOccurrence);

const SERIES_PLACE = {
  location: "Room 4B, Gasperich",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "Passcode 4242",
} satisfies MeetingPlace;
const OWN_PLACE = {
  location: "The annex",
  joinUrl: "https://meet.example/this-week",
  joiningDetails: "Passcode 1111",
} satisfies MeetingPlace;

/** A session that meets in its series' standing place without setting one of its own. */
const inheriting = aScheduledSession({
  place: SERIES_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: SERIES_PLACE,
});

let user: ReturnType<typeof userEvent.setup>;
let rescheduleMutate: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  rescheduleMutate = vi.fn();
  mockUseReschedule.mockReturnValue({
    mutate: rescheduleMutate,
    isPending: false,
    reset: vi.fn(),
  } as never);
  mockUseSkip.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
});

async function openTheDialog(session: Session) {
  render(
    <SessionActions
      slug="g"
      session={session}
      canEditStructure={false}
      canManage={true}
    />,
  );
  await user.click(screen.getByRole("button", { name: /session actions/i }));
  await user.click(screen.getByRole("menuitem", { name: /reschedule/i }));
  return screen.getByLabelText("Join link");
}

function postedPlace(): MeetingPlace {
  const [call] = rescheduleMutate.mock.calls[0] as [
    { input: { place: MeetingPlace } },
  ];
  return call.input.place;
}

describe("SessionActions meeting place", () => {
  /**
   * The reason the session read model keeps the override apart from the resolved place. A block
   * seeded from `place` would hold the inherited place and post it straight back as this
   * occurrence's own, detaching it from the series with nothing on screen saying so.
   */
  it("leaves a session that inherits its place still inheriting when the dialog is confirmed untouched", async () => {
    await openTheDialog(inheriting);
    expect(screen.getByLabelText("Location")).toHaveValue("");
    expect(screen.getByLabelText("Join link")).toHaveValue("");
    expect(screen.getByLabelText("Joining details")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual(EMPTY_PLACE);
  }, 15_000);

  /**
   * Announced, not merely drawn, and announced about the BLOCK. A placeholder is not part of the
   * accessible description, so a blind organizer would hear "Location, blank" and could not tell an
   * inherited place from no place at all - the exact confusion the empty block exists to resolve.
   * Hung off the last field instead of the group, the rule reached only somebody who tabbed that
   * far, which is the same failure one field further along.
   */
  it("announces what an empty block means, so it reads as inheritance", async () => {
    await openTheDialog(inheriting);

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription(
      "All three empty means the series' standing place.",
    );
  }, 15_000);

  /** With nothing to inherit, an empty block already means what it looks like, and the hint is a
   *  line of prose answering a question nobody asked. */
  it("says nothing about inheritance when the series has no place to fall back to", async () => {
    await openTheDialog(aScheduledSession());

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription("");
  }, 15_000);

  /** The fact has to survive typing, which is the other half of what a placeholder cannot do. */
  it("keeps saying what an empty block means once an override is typed", async () => {
    const field = await openTheDialog(inheriting);
    await user.type(field, "https://meet.example/annex");

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription(
      "All three empty means the series' standing place.",
    );
  }, 15_000);

  /**
   * Cancel has to discard. The draft used to be seeded once on the component, so an abandoned
   * place survived the dialog closing and was written by the next confirm - data the organizer
   * had explicitly cancelled.
   */
  it("discards a place typed and then cancelled, rather than writing it on the next confirm", async () => {
    const field = await openTheDialog(inheriting);
    await user.type(field, "https://meet.example/abandoned");
    await user.click(screen.getByRole("button", { name: /cancel/i }));

    await user.click(screen.getByRole("button", { name: /session actions/i }));
    await user.click(screen.getByRole("menuitem", { name: /reschedule/i }));
    expect(screen.getByLabelText("Join link")).toHaveValue("");
    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual(EMPTY_PLACE);
  }, 15_000);

  it("prefills a place the occurrence already set for itself", async () => {
    await openTheDialog(
      aScheduledSession({
        place: OWN_PLACE,
        placeOverride: OWN_PLACE,
        seriesPlace: SERIES_PLACE,
      }),
    );

    expect(screen.getByLabelText("Location")).toHaveValue(OWN_PLACE.location);
    expect(screen.getByLabelText("Join link")).toHaveValue(OWN_PLACE.joinUrl);
    expect(screen.getByLabelText("Joining details")).toHaveValue(
      OWN_PLACE.joiningDetails,
    );
  }, 15_000);

  it("posts an edited place as this occurrence's own", async () => {
    const field = await openTheDialog(inheriting);
    await user.type(field, "https://meet.example/annex");

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual({
      location: null,
      joinUrl: "https://meet.example/annex",
      joiningDetails: null,
    });
  }, 15_000);

  /**
   * Inheritance is all or nothing. An occurrence that names one part is stating where it meets,
   * and the parts it left blank stay blank rather than being filled in from the series - a Teams
   * link overridden beside the series' old passcode is worse than no passcode at all.
   */
  it("never fills a blank part in from the series it is overriding", async () => {
    await openTheDialog(inheriting);
    await user.type(screen.getByLabelText("Location"), "The annex");

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual({
      location: "The annex",
      joinUrl: null,
      joiningDetails: null,
    });
  }, 15_000);

  /**
   * The place is validated server-side now, so this dialog can fail on ordinary organizer input:
   * a scheme-less paste is a 400. It used to close unconditionally, which threw away the whole
   * reschedule - the new date too - and said nothing at all. Losing an edit silently is worse
   * than the wrong place, so the dialog stays open with the typed value intact and the refusal on
   * screen, exactly as the series and one-off forms already behave.
   */
  it("keeps the dialog open with the edit intact and says so when the join link is refused", async () => {
    // A refusal: the mutation reports an error and never reaches its success callback.
    mockUseReschedule.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      reset: vi.fn(),
    } as never);
    const field = await openTheDialog(inheriting);
    await user.type(field, "meet.example/dojo");

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Join link")).toHaveValue("meet.example/dojo");
    expect(screen.getByRole("alert")).toBeInTheDocument();
  }, 15_000);

  it("closes the dialog once the reschedule is accepted", async () => {
    mockUseReschedule.mockReturnValue({
      mutate: vi.fn((_vars, options?: { onSuccess?: () => void }) =>
        options?.onSuccess?.(),
      ),
      isPending: false,
      isError: false,
      reset: vi.fn(),
    } as never);
    await openTheDialog(inheriting);

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  }, 15_000);

  /**
   * The place goes out trimmed. Typing whitespace cannot prove this for the link - `input
   * type="url"` runs the HTML value sanitization algorithm, which strips leading and trailing
   * whitespace before the field's state ever sees it - so the case that does is a stored place
   * that already carries it. The columns have accepted any string under their length cap since
   * they were added, so a row written before the scheme validator existed can hold one, and
   * re-saving it untouched would now be refused by that very validator.
   */
  it("sends a stored place trimmed, so a row written before the validator can still be saved", async () => {
    const untrimmed: MeetingPlace = {
      location: "  The annex  ",
      joinUrl: "  https://meet.example/annex  ",
      joiningDetails: "  Passcode 1111  ",
    };
    await openTheDialog(
      aScheduledSession({
        place: untrimmed,
        placeOverride: untrimmed,
        seriesPlace: SERIES_PLACE,
      }),
    );

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual({
      location: "The annex",
      joinUrl: "https://meet.example/annex",
      joiningDetails: "Passcode 1111",
    });
  }, 15_000);

  it("returns an occurrence to inheritance when its own place is cleared", async () => {
    await openTheDialog(
      aScheduledSession({
        place: OWN_PLACE,
        placeOverride: OWN_PLACE,
        seriesPlace: SERIES_PLACE,
      }),
    );
    await user.clear(screen.getByLabelText("Location"));
    await user.clear(screen.getByLabelText("Join link"));
    await user.clear(screen.getByLabelText("Joining details"));

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(postedPlace()).toEqual(EMPTY_PLACE);
  }, 15_000);
});
