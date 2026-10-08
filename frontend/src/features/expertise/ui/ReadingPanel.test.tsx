import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { buildReading } from "@/features/expertise/ui/expertise-readings";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { ReadingPanel } from "@/features/expertise/ui/ReadingPanel";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function node(canonicalTag: string, over: Partial<TopicNode> = {}): TopicNode {
  return {
    canonicalTag,
    practitionerCount: 1,
    signalCount: 2,
    topHolderShare: 1,
    topHolderName: "Dana Krupa",
    presentedCount: 2,
    contributedCount: 0,
    offeredCount: 0,
    lastActivityAt: "2026-08-01T00:00:00Z",
    openToQuestionsCount: 0,
    ...over,
  };
}

const TOPICS = [
  node("flink"),
  node("kafka", { practitionerCount: 3, signalCount: 8 }),
];
const LINKS: TopicLink[] = [{ a: "flink", b: "kafka", weight: 1 }];
const GROUPS: DrawnGroup[] = [
  {
    tags: ["kafka", "flink"],
    anchor: "kafka",
    name: "around kafka",
    kind: "neighbourhood",
    slot: 0,
  },
];

function panel(over: Partial<Parameters<typeof ReadingPanel>[0]> = {}) {
  return render(
    <ReadingPanel
      topics={TOPICS}
      links={LINKS}
      groups={GROUPS}
      reading="neighbourhoods"
      built={null}
      onReadingChange={vi.fn()}
      selectedTag={null}
      onSelectTag={vi.fn()}
      onSeeWhoKnows={vi.fn()}
      {...over}
    />,
  );
}

test("the neighbourhood summary describes the shape in words", () => {
  panel();

  expect(
    screen.getByText(
      "1 neighbourhood. The largest is around kafka, with 2 areas.",
    ),
  ).toBeInTheDocument();
});

test("the panel names what the readings count and what the rings mean, whichever reading is chosen", () => {
  panel({
    reading: "concentrated",
    built: buildReading("concentrated", TOPICS, new Date()),
  });

  expect(
    screen.getByText(
      "Readings count activity. A dashed ring marks people open to questions. A ring with nothing inside it is an area that was offered and never practised. No reading counts either.",
    ),
  ).toBeInTheDocument();
});

test("a selected area says how many people will take questions on it", () => {
  panel({
    topics: [node("kafka", { practitionerCount: 3, openToQuestionsCount: 2 })],
    groups: [],
    selectedTag: "kafka",
  });

  expect(
    screen.getByText("3 practitioners · 2 people open to questions"),
  ).toBeInTheDocument();
});

test("an area nobody declared says nothing about declarations", () => {
  panel({
    topics: [node("kafka", { practitionerCount: 3 })],
    groups: [],
    selectedTag: "kafka",
  });

  expect(screen.getByText("3 practitioners")).toBeInTheDocument();
});

test("a declaration-only area leads with who is open to questions, not a zero", () => {
  panel({
    topics: [
      node("kafka", {
        practitionerCount: 0,
        signalCount: 0,
        topHolderShare: 0,
        topHolderName: null,
        presentedCount: 0,
        contributedCount: 0,
        offeredCount: 0,
        lastActivityAt: null,
        openToQuestionsCount: 1,
      }),
    ],
    groups: [],
    selectedTag: "kafka",
  });

  expect(screen.getByText("1 person open to questions")).toBeInTheDocument();
  expect(screen.queryByText(/practitioner/)).not.toBeInTheDocument();
});

test("the shape statement agrees noun and count on a single topic in a single neighbourhood", () => {
  panel({
    topics: [node("kafka"), node("flink")],
    links: [{ a: "kafka", b: "flink", weight: 1 }],
    groups: [{ ...GROUPS[0]!, tags: ["kafka"] }],
  });

  expect(
    screen.getByText(
      "1 neighbourhood. The largest is around kafka, with 1 area.",
    ),
  ).toBeInTheDocument();
});

test("the shape statement agrees noun and count when nothing is grouped yet", () => {
  panel({ topics: [node("kafka")], links: [], groups: [] });

  expect(
    screen.getByText(
      "1 area, not yet grouped. 1 area shares no link with any other.",
    ),
  ).toBeInTheDocument();
});

test("a reading shows its statement and its rows with the measured value", () => {
  panel({
    reading: "concentrated",
    built: buildReading("concentrated", TOPICS, new Date()),
  });

  expect(
    screen.getByText("1 of 2 areas rests on a single person."),
  ).toBeInTheDocument();
  expect(screen.getByText("flink")).toBeInTheDocument();
  expect(screen.getByText("100% of 2 · Dana")).toBeInTheDocument();
  expect(screen.queryByText(/Krupa/)).toBeNull();
});

test("a ranked reading truncates to the shown few and says so", () => {
  const staggeredTopics = ["a", "b", "c", "d", "e", "f"].map((tag, index) =>
    node(tag, { lastActivityAt: `2026-0${index + 1}-01T00:00:00Z` }),
  );
  panel({
    reading: "recent",
    built: buildReading("recent", staggeredTopics, new Date()),
    topics: staggeredTopics,
    links: [],
  });

  expect(
    screen.getByText("Showing 5 of 6. Order is the reading, not a verdict."),
  ).toBeInTheDocument();
  expect(screen.getAllByRole("listitem")).toHaveLength(5);
});

test("choosing a reading reports the choice rather than owning it", async () => {
  const onReadingChange = vi.fn();
  panel({ onReadingChange });

  await user.click(screen.getByRole("button", { name: /reading/i }));
  await user.click(screen.getByRole("option", { name: "Not yet presented" }));

  expect(onReadingChange).toHaveBeenCalledWith("unpresented");
});

test("a selected topic offers the one bridge to the search view", async () => {
  const onSeeWhoKnows = vi.fn();
  panel({ selectedTag: "kafka", onSeeWhoKnows });

  await user.click(screen.getByRole("button", { name: "See who knows kafka" }));

  expect(onSeeWhoKnows).toHaveBeenCalledWith("kafka");
});

test("a selected topic lists what it is alongside, with weights", () => {
  panel({ selectedTag: "kafka" });

  const chip = screen.getByRole("button", {
    name: "flink, 1 shared person",
  });
  expect(chip).toHaveTextContent("flink");
  expect(chip).toHaveTextContent("1");
});

test("the adjacency chip's accessible name agrees the shared-person noun with its weight", () => {
  panel({
    selectedTag: "kafka",
    links: [{ a: "flink", b: "kafka", weight: 3 }],
  });

  expect(
    screen.getByRole("button", { name: "flink, 3 shared people" }),
  ).toBeInTheDocument();
});

test("the neighbourhood chip's accessible name names what it sits next to, not a bare count", () => {
  panel();

  expect(
    screen.getByRole("button", {
      name: "kafka, and 1 area alongside it",
    }),
  ).toBeInTheDocument();
});

test("the panel never lists practitioners, which the search view already answers", () => {
  panel({ selectedTag: "kafka" });

  expect(screen.queryByText("Dana Krupa")).not.toBeInTheDocument();
});
