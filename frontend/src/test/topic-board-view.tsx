import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { TopicBoard } from "@/features/mentoring/ui/board/TopicBoard";

/** Shared by the TopicBoard suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

const mutation = () => ({ mutate: vi.fn(), isPending: false });

export const kafka = topicOf({
  id: 1,
  label: "Kafka",
  state: "TODO",
  position: 1,
});
export const rfc = topicOf({
  id: 2,
  label: "Writing an RFC",
  state: "TODO",
  position: 0,
});
export const review = topicOf({
  id: 3,
  label: "Code review",
  state: "IN_PROGRESS",
});
export const testing = topicOf({ id: 4, label: "Testing", state: "DONE" });

export function boardHooks() {
  const write = {
    placeTopic: mutation(),
    addTopic: mutation(),
    updateTopic: mutation(),
    removeTopic: mutation(),
    addPointer: mutation(),
    removePointer: mutation(),
  };
  vi.mocked(useJournalWrite).mockReturnValue(write as never);
  vi.mocked(useExpertiseAreas).mockReturnValue({
    isLoading: false,
    data: [],
  } as never);
  return write;
}

export function boardOf(overrides: Partial<Journal> = {}): ReactElement {
  return (
    <MemoryRouter>
      <TopicBoard
        journal={journalOf({
          topics: [kafka, rfc, review, testing],
          notes: [],
          ...overrides,
        })}
      />
    </MemoryRouter>
  );
}

function boxOf(column: number, position: number | null) {
  if (column < 0) return { left: 0, top: 0, width: 0, height: 0 };
  if (position === null) {
    return { left: column * 300, top: 0, width: 280, height: 200 };
  }
  return {
    left: column * 300 + 10,
    top: 40 + position * 60,
    width: 260,
    height: 50,
  };
}

/** The drag overlay stands where the sticker it copies stands. */
function laidOut(element: HTMLElement): HTMLElement {
  const ghost = element.querySelector<HTMLElement>(":scope > [data-ghost-of]");
  const copied = ghost?.dataset.ghostOf ?? element.dataset.ghostOf;
  if (copied === undefined) return element;
  return (
    document.querySelector<HTMLElement>(`li[data-topic-id="${copied}"]`) ??
    element
  );
}

/** jsdom lays nothing out: columns sit side by side and stickers stack inside them, read from their data attributes. */
export function stubBoardLayout() {
  const columns = ["TODO", "IN_PROGRESS", "DONE"];
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    function (this: HTMLElement) {
      const { dataset } = laidOut(this);
      const column = columns.indexOf(dataset.column ?? dataset.state ?? "");
      const position =
        dataset.position === undefined ? null : Number(dataset.position);
      const { left, top, width, height } = boxOf(column, position);
      return {
        x: left,
        y: top,
        left,
        top,
        width,
        height,
        right: left + width,
        bottom: top + height,
        toJSON: () => ({}),
      };
    },
  );
}
