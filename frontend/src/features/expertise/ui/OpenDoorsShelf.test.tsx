import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { OpenArea } from "@/features/expertise/domain/expertise";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { openDoors: vi.fn() },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

import { OpenDoorsShelf } from "./OpenDoorsShelf";

const gateway = vi.mocked(httpExpertiseGateway);

const DAY = 86_400_000;

function daysAgo(days: number): string {
  return new Date(Date.now() - days * DAY).toISOString();
}

function area(tag: string, people: number, declaredDaysAgo = 30): OpenArea {
  return {
    canonicalTag: tag,
    latestDeclaredAt: daysAgo(declaredDaysAgo),
    people: Array.from({ length: people }, (_, index) => ({
      userId: `${tag}-${index}`,
      displayName: `Person ${index}`,
      avatarUrl: null,
      declaredAt: daysAgo(declaredDaysAgo),
    })),
  };
}

function renderShelf(onOpen = vi.fn()) {
  return render(
    <OpenDoorsShelf slug="guild" onOpen={onOpen} />,
    inQueryClient(),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("OpenDoorsShelf", () => {
  it("rendersOneCardPerArea", async () => {
    // given
    gateway.openDoors.mockResolvedValue([area("kafka", 2), area("rust", 1)]);

    // when
    renderShelf();

    // then
    expect(
      await screen.findByRole("heading", { name: "Open to questions" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "kafka, 2 people open to questions",
      }),
    ).toHaveTextContent("2 people");
    expect(
      screen.getByRole("button", { name: "rust, 1 person open to questions" }),
    ).toHaveTextContent("1 person");
    expect(gateway.openDoors).toHaveBeenCalledWith("guild");
  });

  it("showsAtMostFourFacesPerCard", async () => {
    // given
    gateway.openDoors.mockResolvedValue([area("kafka", 6)]);

    // when
    renderShelf();

    // then
    const card = await screen.findByRole("button", {
      name: "kafka, 6 people open to questions",
    });
    expect(card.querySelectorAll("[data-open='true']")).toHaveLength(4);
    expect(within(card).queryByTestId("open-dot")).not.toBeInTheDocument();
  });

  it("marksAreasNewerThanSevenDaysAsNew", async () => {
    // given
    gateway.openDoors.mockResolvedValue([
      area("kafka", 1, 2),
      area("rust", 1, 8),
    ]);

    // when
    renderShelf();

    // then
    const fresh = await screen.findByRole("button", {
      name: "kafka, 1 person open to questions, new",
    });
    expect(within(fresh).getByText("New")).toBeInTheDocument();
    const old = screen.getByRole("button", {
      name: "rust, 1 person open to questions",
    });
    expect(within(old).queryByText("New")).not.toBeInTheDocument();
  });

  it("hidesBeyondFourBehindAllAreas", async () => {
    // given
    gateway.openDoors.mockResolvedValue(
      ["a", "b", "c", "d", "e", "f"].map((tag) => area(tag, 1)),
    );
    renderShelf();
    const all = await screen.findByRole("button", { name: "All 6 areas" });
    expect(screen.queryByRole("button", { name: /^e,/ })).toBeNull();

    // when
    await userEvent.click(all);

    // then
    expect(screen.getByRole("button", { name: /^e,/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^f,/ })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "All 6 areas" }),
    ).not.toBeInTheDocument();
  });

  it("offersNoAllAreasForFourOrFewer", async () => {
    // given
    gateway.openDoors.mockResolvedValue(
      ["a", "b", "c", "d"].map((tag) => area(tag, 1)),
    );

    // when
    renderShelf();

    // then
    await screen.findByRole("button", { name: /^d,/ });
    expect(
      screen.queryByRole("button", { name: /^All/ }),
    ).not.toBeInTheDocument();
  });

  it("isAbsentWhenNobodyIsOpen", async () => {
    // given
    gateway.openDoors.mockResolvedValue([]);

    // when
    const { container } = renderShelf();

    // then
    await waitFor(() => expect(gateway.openDoors).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("isAbsentOnAFailedRead", async () => {
    // given
    gateway.openDoors.mockRejectedValue(new Error("boom"));

    // when
    const { container } = renderShelf();

    // then
    await waitFor(() => expect(gateway.openDoors).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("openingACardHandsOverTheArea", async () => {
    // given
    gateway.openDoors.mockResolvedValue([area("kafka", 2)]);
    const onOpen = vi.fn();
    renderShelf(onOpen);

    // when
    await userEvent.click(
      await screen.findByRole("button", { name: /^kafka/ }),
    );

    // then
    expect(onOpen).toHaveBeenCalledWith("kafka");
  });
});
