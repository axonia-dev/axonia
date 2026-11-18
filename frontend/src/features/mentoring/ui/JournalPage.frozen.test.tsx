import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useJournal } from "@/features/mentoring/api/use-mentoring";
import { journal } from "@/features/mentoring/test-data";
import { JournalPage } from "@/features/mentoring/ui/JournalPage";
import { ApiError } from "@/lib/api-client";
import { hooks, show } from "@/test/journal-page-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournal: vi.fn(),
  useEndPairing: vi.fn(),
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
  useWords: vi.fn(),
  useShowing: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("JournalPage when the journal is frozen, shared or unavailable", () => {
  it("an ended journal hides every composer and shows its finale", () => {
    // given
    hooks();
    const ended = { ...journal.pairing, endedAt: "2026-12-01T10:00:00Z" };

    // when
    show({ pairing: ended, writable: false });

    // then
    expect(
      screen.queryByRole("button", { name: "Add note" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add topic" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "End mentoring" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Write a postcard" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Ended")).toBeInTheDocument();
  });

  it("ends the mentoring after a confirmation", async () => {
    // given
    const { end } = hooks();
    show();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "End mentoring" }),
    );
    const dialog = screen.getByRole("alertdialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "End mentoring" }),
    );

    // then
    expect(end.mutate).toHaveBeenCalled();
  });

  it("offers the postcard right after the mentee ends it", async () => {
    // given
    const { end } = hooks();
    end.mutate.mockImplementation(
      (_: unknown, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    show();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "End mentoring" }),
    );
    await userEvent.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "End mentoring",
      }),
    );

    // then
    expect(screen.getByRole("dialog", { name: "To Ada" })).toBeInTheDocument();
  });

  it("does not hand the mentor a postcard on ending", async () => {
    // given
    const { end } = hooks();
    end.mutate.mockImplementation(
      (_: unknown, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    show({ pairing: { ...journal.pairing, myRole: "MENTOR" } });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "End mentoring" }),
    );
    await userEvent.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "End mentoring",
      }),
    );

    // then
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("a deleted partner reads Deleted member", () => {
    // given
    hooks();

    // when
    show({ pairing: { ...journal.pairing, partnerName: "" } });

    // then
    expect(
      screen.getByRole("heading", { name: "Deleted member" }),
    ).toBeInTheDocument();
  });

  it("asks a delegated session for the person's own sign-in and requests nothing", () => {
    // given
    hooks();
    vi.mocked(useIsDelegated).mockReturnValue(true);

    // when
    show();

    // then
    expect(
      screen.getByText("Mentoring needs the person’s own sign-in."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Giving feedback")).not.toBeInTheDocument();
    expect(useJournal).toHaveBeenCalledWith(11);
  });

  it("a journal id that is not a number is not found, with no request", () => {
    // given
    hooks();
    vi.mocked(useJournal).mockReturnValue({
      isLoading: false,
      data: undefined,
    } as never);

    // when
    render(
      <MemoryRouter initialEntries={["/me/mentoring/abc"]}>
        <Routes>
          <Route path="/me/mentoring/:pairingId" element={<JournalPage />} />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText("This journal was not found.")).toBeInTheDocument();
    expect(useJournal).toHaveBeenCalledWith(Number.NaN);
  });

  it.each([
    ["cannot be loaded", new ApiError(500, "boom"), "alert"],
    ["is not the reader's", new ApiError(404, "missing"), "calm"],
  ])("says when the journal %s", (_name, error, tone) => {
    // given
    hooks();
    vi.mocked(useJournal).mockReturnValue({
      isLoading: false,
      data: undefined,
      error,
    } as never);

    // when
    render(
      <MemoryRouter initialEntries={["/me/mentoring/11"]}>
        <Routes>
          <Route path="/me/mentoring/:pairingId" element={<JournalPage />} />
        </Routes>
      </MemoryRouter>,
    );

    // then
    if (tone === "alert") {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Could not load this journal.",
      );
    } else {
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(
        screen.getByText("This journal was not found."),
      ).toBeInTheDocument();
    }
  });
});
