import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";

import type { TopicNode } from "@/features/expertise/domain/expertise";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { ReadingPanel } from "@/features/expertise/ui/ReadingPanel";

function node(canonicalTag: string): TopicNode {
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
  };
}

function named(name: string, tags: string[]): DrawnGroup {
  return { tags, anchor: tags[0]!, name, kind: "named", slot: 0 };
}

function around(tags: string[]): DrawnGroup {
  return {
    tags,
    anchor: tags[0]!,
    name: `around ${tags[0]!}`,
    kind: "neighbourhood",
    slot: 0,
  };
}

function panel(groups: DrawnGroup[], selectedTag: string | null = null) {
  return render(
    <ReadingPanel
      topics={[node("kafka"), node("flink")]}
      links={[{ a: "flink", b: "kafka", weight: 1 }]}
      groups={groups}
      reading="neighbourhoods"
      built={null}
      onReadingChange={vi.fn()}
      selectedTag={selectedTag}
      onSelectTag={vi.fn()}
      onSeeWhoKnows={vi.fn()}
    />,
  );
}

test("the shape statement states the split between named and detected", () => {
  panel([
    named("Data in Motion", ["kafka", "flink", "debezium"]),
    named("Testing", ["tdd"]),
    named("Frontend", ["react"]),
    named("Systems", ["rust"]),
    around(["go", "grpc"]),
    around(["css"]),
    around(["sql"]),
  ]);

  expect(
    screen.getByText(
      "4 named areas, 3 detected. The largest is Data in Motion, with 3 areas.",
    ),
  ).toBeInTheDocument();
});

test("the split agrees noun and count on one of each", () => {
  panel([named("Testing", ["kafka"]), around(["flink", "tdd"])]);

  expect(
    screen.getByText(
      "1 named area, 1 detected. The largest is around flink, with 2 areas.",
    ),
  ).toBeInTheDocument();
});

test("a map of named areas only counts them alone", () => {
  panel([named("Streaming", ["kafka", "flink"]), named("Testing", ["tdd"])]);

  expect(
    screen.getByText("2 named areas. The largest is Streaming, with 2 areas."),
  ).toBeInTheDocument();
});

test("a single named area agrees its noun", () => {
  panel([named("Streaming", ["kafka", "flink"])]);

  expect(
    screen.getByText("1 named area. The largest is Streaming, with 2 areas."),
  ).toBeInTheDocument();
});

test("a map with nothing named reads as it always has", () => {
  panel([around(["kafka", "flink"]), around(["tdd"]), around(["css"])]);

  expect(
    screen.getByText(
      "3 neighbourhoods. The largest is around kafka, with 2 areas.",
    ),
  ).toBeInTheDocument();
});

test("a named area's chip carries its own name and says it was named", () => {
  panel([named("Data in Motion", ["kafka", "flink"])]);

  const chip = screen.getByRole("button", {
    name: "Data in Motion, named area with 2 areas",
  });
  expect(chip).toHaveTextContent("Data in Motion");
  expect(chip).toHaveAttribute("data-kind", "named");
});

test("an area inside a named area says which one", () => {
  panel([named("Data in Motion", ["kafka", "flink"])], "kafka");

  expect(screen.getByText(/· in Data in Motion$/)).toBeInTheDocument();
});

test("an area inside a neighbourhood still says what it sits around", () => {
  panel([around(["flink", "kafka"])], "kafka");

  expect(screen.getByText(/· around flink$/)).toBeInTheDocument();
});

test.each([
  ["Groups", [named("Streaming", ["kafka"]), around(["flink"])]],
  ["Neighbourhoods", [around(["kafka", "flink"])]],
])(
  "the base rendering is offered as %s",
  async (label, groups: DrawnGroup[]) => {
    const user = userEvent.setup({ delay: null });
    panel(groups);

    await user.click(screen.getByRole("button", { name: /reading/i }));

    expect(screen.getByRole("option", { name: label })).toBeInTheDocument();
    expect(
      screen.getAllByRole("option").map((o) => o.textContent),
    ).not.toContain(label === "Groups" ? "Neighbourhoods" : "Groups");
  },
);

test.each([
  ["All groups", [named("Streaming", ["kafka"]), around(["flink"])]],
  ["All neighbourhoods", [around(["kafka", "flink"])]],
])("the way back from an area reads %s", (label, groups: DrawnGroup[]) => {
  panel(groups, "kafka");

  expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
});

// The map's order is its colours, detected first; the list is the leader's, named first, and a tie goes to the named.
test("lists the named areas first, whatever order the map draws them in", () => {
  panel([around(["go", "grpc"]), named("Streaming", ["kafka", "flink"])]);

  expect(
    screen
      .getAllByRole("button", { name: /named area|alongside/ })
      .map((b) => b.dataset["kind"]),
  ).toStrictEqual(["named", "neighbourhood"]);
  expect(
    screen.getByText(
      "1 named area, 1 detected. The largest is Streaming, with 2 areas.",
    ),
  ).toBeInTheDocument();
});
