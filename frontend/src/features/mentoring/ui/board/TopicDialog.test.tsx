import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal, Topic } from "@/features/mentoring/domain/mentoring";
import {
  journalOf,
  noteOf,
  pointerOf,
  topicOf,
} from "@/features/mentoring/test-data";
import { TopicDialog } from "@/features/mentoring/ui/board/TopicDialog";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));

const topic: Topic = topicOf();
const mutation = () => ({ mutate: vi.fn(), isPending: false });

function show(shown: Topic, overrides: Partial<Journal> = {}) {
  const write = {
    updateTopic: mutation(),
    removeTopic: mutation(),
    addPointer: mutation(),
    removePointer: mutation(),
  };
  vi.mocked(useJournalWrite).mockReturnValue(write as never);
  vi.mocked(useExpertiseAreas).mockReturnValue({
    isLoading: false,
    data: [{ canonicalTag: "kubernetes" }, { canonicalTag: "testing" }],
  } as never);
  const onClose = vi.fn();
  render(
    <MemoryRouter>
      <TopicDialog
        journal={journalOf({ topics: [shown], ...overrides })}
        topic={shown}
        onClose={onClose}
      />
    </MemoryRouter>,
  );
  return { write, onClose };
}

async function pickArea(name: string) {
  await userEvent.click(
    screen.getByRole("button", { name: (label) => label.includes("Area") }),
  );
  await userEvent.click(screen.getByRole("option", { name }));
  await userEvent.click(screen.getByRole("button", { name: "Save topic" }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("TopicDialog editing", () => {
  it("renames a topic on Enter", async () => {
    // given
    const { write } = show(topic);
    const input = screen.getByRole("textbox", { name: "Name" });

    // when
    await userEvent.clear(input);
    await userEvent.type(input, "Code review{Enter}");

    // then
    expect(write.updateTopic.mutate).toHaveBeenCalledWith(
      { id: topic.id, topic: { label: "Code review", area: null } },
      expect.anything(),
    );
  });

  it("links a topic to an area of the guild", async () => {
    // given
    const { write } = show(topic);

    // when
    await pickArea("kubernetes");

    // then
    expect(write.updateTopic.mutate).toHaveBeenCalledWith(
      { id: topic.id, topic: { label: topic.label, area: "kubernetes" } },
      expect.anything(),
    );
  });

  it("unlinks a topic from its area", async () => {
    // given
    const { write } = show({ ...topic, area: "testing" });

    // when
    await pickArea("No area");

    // then
    expect(write.updateTopic.mutate).toHaveBeenCalledWith(
      { id: topic.id, topic: { label: topic.label, area: null } },
      expect.anything(),
    );
  });

  it("removes a topic only once confirmed, then closes", async () => {
    // given
    const { write, onClose } = show(topic);
    write.removeTopic.mutate.mockImplementation(
      (_id: number, options: { onSuccess: () => void }) => options.onSuccess(),
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove topic" }));
    await userEvent.click(
      within(
        screen.getByRole("dialog", { name: `Confirm removing ${topic.label}` }),
      ).getByRole("button", { name: "Remove" }),
    );

    // then
    expect(write.removeTopic.mutate).toHaveBeenCalledWith(
      topic.id,
      expect.anything(),
    );
    expect(onClose).toHaveBeenCalled();
  });
});

describe("TopicDialog links and notes", () => {
  it("adds a link on Enter and says inline why an address is refused", async () => {
    // given
    const { write } = show(topic);
    write.addPointer.mutate.mockImplementation(
      (_pointer: unknown, options: { onError: (error: unknown) => void }) =>
        options.onError(
          new ApiError(400, "Not a web address", "field_invalid"),
        ),
    );
    await userEvent.click(
      screen.getByRole("button", { name: `Add link to ${topic.label}` }),
    );
    const field = screen.getByRole("textbox", {
      name: `Address to link from ${topic.label}`,
    });

    // when
    await userEvent.type(field, "example.test{Enter}");

    // then
    expect(write.addPointer.mutate).toHaveBeenCalledWith(
      { topicId: topic.id, noteId: null, url: "example.test" },
      expect.anything(),
    );
    expect(
      screen.getByText("Paste a full web address starting with https."),
    ).toBeInTheDocument();
  });

  it("shows a pointer's title as a link and an unavailable one as Unavailable", () => {
    // when
    show({
      ...topic,
      pointers: [
        pointerOf(),
        pointerOf({ id: 6, title: null, href: null, available: false }),
      ],
    });

    // then
    expect(screen.getByRole("link", { name: "Kafka basics" })).toHaveAttribute(
      "href",
      "/communities/platform/sessions/9",
    );
    expect(screen.getByText("Unavailable")).toBeInTheDocument();
  });

  it("lists the notes that name the topic", () => {
    // when
    show(topic, {
      notes: [
        noteOf({ id: 2, body: "Covered feedback.", topicIds: [topic.id] }),
        noteOf({ id: 3, body: "Unrelated.", topicIds: [] }),
      ],
    });

    // then
    expect(screen.getByText("Covered feedback.")).toBeInTheDocument();
    expect(screen.queryByText("Unrelated.")).not.toBeInTheDocument();
  });

  it("a read-only journal shows the area and changes nothing", () => {
    // when
    show({ ...topic, area: "testing" }, { writable: false });

    // then
    expect(screen.getByText("testing")).toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: "Name" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Remove topic" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("No note names it yet.")).toBeInTheDocument();
  });
});
