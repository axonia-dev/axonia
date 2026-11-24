import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LibraryView } from "@/features/knowledge/ui/LibraryView";

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useLibraryPage: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/LibraryHeader", () => ({
  LibraryHeader: () => null,
}));
vi.mock("@/features/knowledge/ui/TypeChips", () => ({
  ALL_TYPES: 0,
  TypeChips: ({ onSelect }: { onSelect: (id: number) => void }) => (
    <button type="button" onClick={() => onSelect(3)}>
      pick-type
    </button>
  ),
}));
vi.mock("@/features/knowledge/ui/SessionFilter", () => ({
  ANY_SESSION: -1,
  NO_SESSION: 0,
  SessionFilter: ({ onChange }: { onChange: (id: number) => void }) => (
    <button type="button" onClick={() => onChange(0)}>
      pick-no-session
    </button>
  ),
}));
vi.mock("@/features/knowledge/ui/SessionContextStrip", () => ({
  SessionContextStrip: () => null,
}));
vi.mock("@/features/knowledge/ui/ArtifactList", () => ({
  ArtifactList: () => null,
}));

import { useLibraryPage } from "@/features/knowledge/api/use-artifacts";

const mockArtifacts = vi.mocked(useLibraryPage);

function LocationProbe() {
  const navigate = useNavigate();
  return (
    <>
      <span data-testid="search">{useLocation().search}</span>
      <button
        type="button"
        onClick={() => void navigate("/communities/guild/library?q=rust")}
      >
        follow-link
      </button>
    </>
  );
}

function renderView(entry = "/communities/guild/library") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <LibraryView slug="guild" />
      <LocationProbe />
    </MemoryRouter>,
  );
}

function mockPages(totalPages: number) {
  mockArtifacts.mockReturnValue({
    data: { content: [], totalElements: 0, totalPages, number: 0, size: 20 },
    isLoading: false,
    isPlaceholderData: false,
    isError: false,
  } as never);
}

function lastParams() {
  return mockArtifacts.mock.calls.at(-1)![1];
}

function urlSearch() {
  return new URLSearchParams(screen.getByTestId("search").textContent);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockPages(5);
});

describe("LibraryView URL state", () => {
  it("opens on exactly the list a shared link names", () => {
    // when
    renderView(
      "/communities/guild/library?q=kafka&type=3&tag=ops&session=9&sort=mostAppreciated&page=3",
    );

    // then
    expect(lastParams()).toEqual({
      page: 2,
      size: 20,
      q: "kafka",
      type: 3,
      tag: "ops",
      session: 9,
      sort: "mostAppreciated",
    });
    expect(
      screen.getByRole("searchbox", { name: /search the library/i }),
    ).toHaveValue("kafka");
  });

  it("writes the page to the URL, one-based, and none on the first", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "Next" }));

    // then
    expect(urlSearch().get("page")).toBe("2");
    expect(lastParams()).toMatchObject({ page: 1 });

    // when
    await user.click(screen.getByRole("button", { name: "Previous" }));

    // then
    expect(urlSearch().has("page")).toBe(false);
  });

  it("writes a chosen filter to the URL and starts again from the first page", async () => {
    // given
    renderView("/communities/guild/library?page=3");

    // when
    await user.click(screen.getByRole("button", { name: "pick-type" }));

    // then
    expect(urlSearch().get("type")).toBe("3");
    expect(urlSearch().has("page")).toBe(false);
    expect(lastParams()).toMatchObject({ type: 3, page: 0 });
  });

  it("spells the No session facet in the URL and reads it back as session 0", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "pick-no-session" }));

    // then
    expect(urlSearch().get("session")).toBe("none");
    expect(lastParams()).toMatchObject({ session: 0 });
  });

  it("writes the search once typing pauses, from the first page", async () => {
    // given
    renderView("/communities/guild/library?page=2");

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /search the library/i }),
      "kafka ",
    );

    // then
    await waitFor(() => expect(urlSearch().get("q")).toBe("kafka"));
    expect(urlSearch().has("page")).toBe(false);
    expect(lastParams()).toMatchObject({ q: "kafka", page: 0 });
  });

  it("follows the URL when a link moves the query on its own", async () => {
    // given
    renderView("/communities/guild/library?q=kafka");

    // when
    await user.click(screen.getByRole("button", { name: "follow-link" }));

    // then
    expect(
      screen.getByRole("searchbox", { name: /search the library/i }),
    ).toHaveValue("rust");
    expect(lastParams()).toMatchObject({ q: "rust" });
  });

  it("brings a page past the end back to the last page there is", async () => {
    // given
    mockPages(2);

    // when
    renderView("/communities/guild/library?page=7");

    // then
    await waitFor(() => expect(urlSearch().get("page")).toBe("2"));
    expect(lastParams()).toMatchObject({ page: 1 });
  });

  it("ignores a page or type the URL cannot mean", () => {
    // when
    renderView("/communities/guild/library?page=abc&type=-2&session=x");

    // then
    expect(lastParams()).toEqual({ page: 0, size: 20 });
  });
});
