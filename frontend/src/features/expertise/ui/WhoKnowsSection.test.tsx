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

function person(userId: string, over: Partial<Practitioner> = {}) {
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
  } as Practitioner;
}

function renderSection(onOpenSearch?: (tag: string) => void) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <WhoKnowsSection
        slug="acme"
        tag="kafka"
        {...(onOpenSearch ? { onOpenSearch } : {})}
      />
    </QueryClientProvider>,
  );
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

  const { container } = renderSection();

  await waitFor(() => expect(gateway.search).toHaveBeenCalled());
  expect(container).toBeEmptyDOMElement();
});

test("an empty answer renders nothing", async () => {
  gateway.search.mockResolvedValue([]);

  const { container } = renderSection();

  await waitFor(() => expect(gateway.search).toHaveBeenCalled());
  expect(container).toBeEmptyDOMElement();
});
