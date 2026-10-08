import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    namedAreas: vi.fn(),
    createNamedArea: vi.fn(),
    renameNamedArea: vi.fn(),
    deleteNamedArea: vi.fn(),
    placeInNamedArea: vi.fn(),
    removeFromNamedArea: vi.fn(),
  },
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import {
  useNamedAreaActions,
  useNamedAreas,
} from "@/features/expertise/api/use-named-areas";
import { ApiError } from "@/lib/api-client";

const gateway = vi.mocked(httpExpertiseGateway);
const notifyMock = vi.mocked(notify);
const notifyErrorMock = vi.mocked(notifyError);

const STREAMING = { id: 7, name: "Streaming", tags: ["kafka"] };

let client: QueryClient;
let invalidateSpy: ReturnType<typeof vi.spyOn>;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function renderActions() {
  return renderHook(() => useNamedAreaActions("guild"), { wrapper }).result;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  invalidateSpy = vi.spyOn(client, "invalidateQueries");
  gateway.createNamedArea.mockResolvedValue({ id: 8, name: "Data", tags: [] });
  gateway.renameNamedArea.mockResolvedValue(undefined);
  gateway.deleteNamedArea.mockResolvedValue(undefined);
  gateway.placeInNamedArea.mockResolvedValue(undefined);
  gateway.removeFromNamedArea.mockResolvedValue(undefined);
});

describe("useNamedAreas", () => {
  it("reads the community's named areas under the curation key", async () => {
    // given
    gateway.namedAreas.mockResolvedValue([STREAMING]);

    // when
    const { result } = renderHook(() => useNamedAreas("guild"), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([STREAMING]);
    expect(gateway.namedAreas.mock.calls[0]).toEqual(["guild"]);
    expect(client.getQueryData(expertiseKeys.namedAreas("guild"))).toEqual([
      STREAMING,
    ]);
    expect(expertiseKeys.namedAreas("guild").slice(0, 3)).toEqual(
      expertiseKeys.curation("guild"),
    );
  });
});

describe("useNamedAreaActions", () => {
  const writes: [string, (a: ReturnType<typeof renderActions>) => void][] = [
    ["create", (a) => a.current.createNamedArea("Data")],
    ["rename", (a) => a.current.renameNamedArea(7, "Storage")],
    ["delete", (a) => a.current.deleteNamedArea(STREAMING)],
    ["place", (a) => a.current.placeInNamedArea(7, "postgres")],
    ["remove", (a) => a.current.removeFromNamedArea("kafka")],
  ];

  const MAP = expertiseKeys.map("guild");
  const CONTRIBUTIONS = expertiseKeys.mapContributions("guild");
  const VOCABULARY = expertiseKeys.curationVocabulary("guild");
  const NAMED_AREAS = expertiseKeys.namedAreas("guild");

  function seedCache() {
    [MAP, CONTRIBUTIONS, VOCABULARY, NAMED_AREAS].forEach((key) =>
      client.setQueryData(key, []),
    );
  }

  function stale(key: readonly unknown[]) {
    return client.getQueryState(key)!.isInvalidated;
  }

  describe.each(writes)("%s", (_verb, act) => {
    it("marks the map and the named areas stale", async () => {
      // given
      seedCache();

      // when
      act(renderActions());

      // then
      await waitFor(() => expect(stale(NAMED_AREAS)).toBe(true));
      expect(stale(MAP)).toBe(true);
    });

    it("leaves the map's contributions and the vocabulary alone", async () => {
      // given - contributions sit under the map's key, up to 5000 of them
      seedCache();

      // when
      act(renderActions());

      // then
      await waitFor(() => expect(stale(NAMED_AREAS)).toBe(true));
      expect(stale(CONTRIBUTIONS)).toBe(false);
      expect(stale(VOCABULARY)).toBe(false);
    });
  });

  it("creates a named area", async () => {
    // when
    renderActions().current.createNamedArea("Data");

    // then
    await waitFor(() =>
      expect(notifyMock).toHaveBeenCalledWith("Created Data"),
    );
    expect(gateway.createNamedArea.mock.calls[0]).toEqual(["guild", "Data"]);
    expect(gateway.placeInNamedArea).not.toHaveBeenCalled();
  });

  it("creates a named area already holding a neighbourhood's members", async () => {
    // when
    renderActions().current.createNamedArea("Data", ["postgres", "kafka"]);

    // then
    await waitFor(() =>
      expect(notifyMock).toHaveBeenCalledWith("Created Data"),
    );
    expect(gateway.placeInNamedArea.mock.calls).toEqual([
      ["guild", 8, "postgres"],
      ["guild", 8, "kafka"],
    ]);
  });

  it("says a create landed when one of its placements fails", async () => {
    // given
    gateway.placeInNamedArea
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error("boom"));

    // when
    renderActions().current.createNamedArea("Data", ["postgres", "kafka"]);

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith(
        "Created Data, but some tags were not placed.",
      ),
    );
    expect(notifyMock).not.toHaveBeenCalled();
    expect(gateway.placeInNamedArea).toHaveBeenCalledTimes(2);
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: expertiseKeys.namedAreas("guild"),
      }),
    );
  });

  it("renames a named area", async () => {
    // when
    renderActions().current.renameNamedArea(7, "Storage");

    // then
    await waitFor(() =>
      expect(notifyMock).toHaveBeenCalledWith("Renamed to Storage"),
    );
    expect(gateway.renameNamedArea.mock.calls[0]).toEqual([
      "guild",
      7,
      "Storage",
    ]);
  });

  it("deletes a named area and names it", async () => {
    // when
    renderActions().current.deleteNamedArea(STREAMING);

    // then
    await waitFor(() =>
      expect(notifyMock).toHaveBeenCalledWith("Deleted Streaming"),
    );
    expect(gateway.deleteNamedArea.mock.calls[0]).toEqual(["guild", 7]);
  });

  it("places and removes a tag without a toast", async () => {
    // given
    const actions = renderActions();

    // when
    actions.current.placeInNamedArea(7, "postgres");
    actions.current.removeFromNamedArea("kafka");

    // then
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledTimes(4));
    expect(gateway.placeInNamedArea.mock.calls[0]).toEqual([
      "guild",
      7,
      "postgres",
    ]);
    expect(gateway.removeFromNamedArea.mock.calls[0]).toEqual([
      "guild",
      "kafka",
    ]);
    expect(notifyMock).not.toHaveBeenCalled();
  });

  it.each([
    ["create", "Couldn’t create that named area."],
    ["rename", "Couldn’t rename that named area."],
    ["delete", "Couldn’t delete that named area."],
    ["place", "Couldn’t place that tag."],
    ["remove", "Couldn’t take that tag out."],
  ])("reports a failed %s", async (verb, message) => {
    // given
    const failure = new Error("boom");
    gateway.createNamedArea.mockRejectedValue(failure);
    gateway.renameNamedArea.mockRejectedValue(failure);
    gateway.deleteNamedArea.mockRejectedValue(failure);
    gateway.placeInNamedArea.mockRejectedValue(failure);
    gateway.removeFromNamedArea.mockRejectedValue(failure);
    const act = writes.find(([w]) => w === verb)![1];

    // when
    act(renderActions());

    // then
    await waitFor(() => expect(notifyErrorMock).toHaveBeenCalledWith(message));
  });

  it("says the named area is gone when another tab deleted it", async () => {
    // given
    gateway.renameNamedArea.mockRejectedValue(new ApiError(404, "detail"));

    // when
    renderActions().current.renameNamedArea(7, "Storage");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith(
        "That named area no longer exists.",
      ),
    );
  });

  it("says why the backend refused a placement", async () => {
    // given
    gateway.placeInNamedArea.mockRejectedValue(
      new ApiError(400, "detail", "TAG_NOT_IN_COMMUNITY"),
    );

    // when
    renderActions().current.placeInNamedArea(7, "cobol");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith(
        "This community doesn't use that tag.",
      ),
    );
  });
});
