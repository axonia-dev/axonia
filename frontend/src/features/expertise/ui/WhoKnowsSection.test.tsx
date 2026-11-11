import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import type { Practitioner } from "@/features/expertise/domain/expertise";
import { WhoKnowsSection } from "@/features/expertise/ui/WhoKnowsSection";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { search: vi.fn() },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

function person(
  userId: string,
  over: Partial<Practitioner> = {},
): Practitioner {
  return {
    userId,
    displayName: userId,
    avatarUrl: null,
    orgUnit: null,
    tier: "PRACTITIONER",
    evidence: {
      presentedCount: 2,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    openToQuestions: false,
    declaredAt: null,
    ...over,
  };
}

function renderSection(onOpenSearch?: (tag: string) => void, cueMs?: number) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const rendered = render(
    <QueryClientProvider client={client}>
      <WhoKnowsSection
        slug="acme"
        tag="kafka"
        {...(onOpenSearch ? { onOpenSearch } : {})}
        {...(cueMs === undefined ? {} : { cueMs })}
      />
    </QueryClientProvider>,
  );
  return { ...rendered, client };
}

function settledStatus(client: QueryClient) {
  return client.getQueryCache().getAll()[0]?.state.status;
}

beforeEach(() => {
  vi.clearAllMocks();
});

test("lists at most four people in the search's order under the heading", async () => {
  gateway.search.mockResolvedValue(
    ["ana", "bo", "cy", "di", "ed"].map((id) => person(id)),
  );

  renderSection();

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
  const rows = screen.getAllByRole("listitem");
  expect(rows.map((row) => row.textContent)).toEqual([
    expect.stringContaining("ana"),
    expect.stringContaining("bo"),
    expect.stringContaining("cy"),
    expect.stringContaining("di"),
  ]);
  expect(screen.getAllByText("Practitioner")).toHaveLength(4);
  expect(screen.getAllByText("hosted 2 sessions")).toHaveLength(4);
  expect(gateway.search).toHaveBeenCalledWith("acme", {
    term: "kafka",
    demonstratedOnly: false,
  });
});

test("Open in Search hands the tag over", async () => {
  gateway.search.mockResolvedValue([person("ana")]);
  const onOpenSearch = vi.fn();
  renderSection(onOpenSearch);

  await userEvent.click(
    await screen.findByRole("button", { name: "Open in Search" }),
  );

  expect(onOpenSearch).toHaveBeenCalledWith("kafka");
});

test("without a handler there is no Open in Search", async () => {
  gateway.search.mockResolvedValue([person("ana")]);

  renderSection();

  await screen.findByText("ana");
  expect(
    screen.queryByRole("button", { name: "Open in Search" }),
  ).not.toBeInTheDocument();
});

test("a failed search renders nothing", async () => {
  gateway.search.mockRejectedValue(new Error("boom"));

  const { container, client } = renderSection();

  await waitFor(() => expect(settledStatus(client)).toBe("error"));
  expect(container).toBeEmptyDOMElement();
});

test("an empty answer renders nothing", async () => {
  gateway.search.mockResolvedValue([]);

  const { container, client } = renderSection();

  await waitFor(() => expect(settledStatus(client)).toBe("success"));
  expect(container).toBeEmptyDOMElement();
});

test("the rows follow the heading by one step each", async () => {
  gateway.search.mockResolvedValue([person("ana"), person("bo")]);

  renderSection();

  await screen.findByRole("heading", { name: "Who knows kafka" });
  expect(
    screen.getAllByRole("listitem").map((row) => row.style.animationDelay),
  ).toEqual(["90ms", "180ms"]);
});

test("with a cue the section stays out of sight until the echo has crossed the map", async () => {
  gateway.search.mockResolvedValue([person("ana")]);

  const { client } = renderSection(undefined, 300);

  await waitFor(() => expect(settledStatus(client)).toBe("success"));
  expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
});

test("openPeopleWearTheRingWithoutTheDot", async () => {
  // given
  gateway.search.mockResolvedValue([
    person("ana", {
      openToQuestions: true,
      declaredAt: "2026-09-12T12:00:00Z",
    }),
    person("bo"),
  ]);

  // when
  const { container } = renderSection();

  // then
  await screen.findByRole("heading", { name: "Who knows kafka" });
  const rings = container.querySelectorAll("[data-open='true']");
  expect(rings).toHaveLength(1);
  expect(screen.getAllByRole("listitem")[0]).toContainElement(
    rings[0] as HTMLElement,
  );
  expect(screen.queryByTestId("open-dot")).not.toBeInTheDocument();
  expect(screen.getAllByRole("listitem")[0]).toHaveTextContent(
    "Open to questions",
  );
  expect(screen.getByText("Open to questions")).toHaveClass("sr-only");
  expect(screen.getAllByRole("listitem")[1]).not.toHaveTextContent(
    "Open to questions",
  );
});
