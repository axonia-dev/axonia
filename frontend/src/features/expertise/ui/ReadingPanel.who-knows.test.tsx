import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import type {
  Practitioner,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { ReadingPanel } from "@/features/expertise/ui/ReadingPanel";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { search: vi.fn() },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

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

const GROUPS: DrawnGroup[] = [
  {
    tags: ["kafka", "rust"],
    anchor: "kafka",
    name: "around kafka",
    kind: "neighbourhood",
    slot: 0,
  },
];

const ANA = {
  userId: "ana",
  displayName: "Ana",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER",
  evidence: {
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
} as Practitioner;

function panel(over: Partial<Parameters<typeof ReadingPanel>[0]> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ReadingPanel
        topics={[node("kafka"), node("rust")]}
        links={[{ a: "kafka", b: "rust", weight: 1 }]}
        groups={GROUPS}
        reading="neighbourhoods"
        built={null}
        onReadingChange={vi.fn()}
        selectedTag={null}
        onSelectTag={vi.fn()}
        onSeeWhoKnows={vi.fn()}
        matchedAreas={[]}
        slug="acme"
        onChooseMatchedArea={vi.fn()}
        echoing={false}
        {...over}
      />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  gateway.search.mockResolvedValue([ANA]);
});

test("one matched area shows who knows it, with a way into Search", async () => {
  const onSeeWhoKnows = vi.fn();
  panel({ matchedAreas: ["kafka"], onSeeWhoKnows });

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Open in Search" }));
  expect(onSeeWhoKnows).toHaveBeenCalledWith("kafka");
});

test("several matched areas are named as buttons, not listed", async () => {
  const onChooseMatchedArea = vi.fn();
  panel({ matchedAreas: ["kafka", "rust"], onChooseMatchedArea });

  expect(screen.getByText("Matches 2 areas:")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "kafka" })).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "rust" }));
  expect(onChooseMatchedArea).toHaveBeenCalledWith("rust");
  expect(gateway.search).not.toHaveBeenCalled();
});

test("no matched area shows neither", () => {
  panel();

  expect(screen.queryByText(/Matches \d+ areas/)).not.toBeInTheDocument();
  expect(screen.queryByText(/Who knows/)).not.toBeInTheDocument();
});

test("a selected area shows who knows it without Open in Search", async () => {
  panel({ selectedTag: "kafka" });

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Open in Search" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "See who knows kafka" }),
  ).toBeInTheDocument();
});

test("a term and a selected area never list people twice", async () => {
  panel({ selectedTag: "rust", matchedAreas: ["kafka"] });

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
  expect(screen.getAllByRole("heading", { name: /Who knows/ })).toHaveLength(1);
  expect(
    screen.getByRole("button", { name: "See who knows rust" }),
  ).toBeInTheDocument();
});

test("a selected area's section shows without waiting for an echo", async () => {
  panel({ selectedTag: "kafka" });

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
});

test("an echoing term keeps its section out of sight at first", async () => {
  panel({ matchedAreas: ["kafka"], echoing: true });

  await waitFor(() => expect(gateway.search).toHaveBeenCalled());
  await new Promise((resolve) => setTimeout(resolve, 50));
  expect(screen.queryByRole("heading", { name: /Who knows/ })).toBeNull();
});
