import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    renameTag: vi.fn(),
    deleteTag: vi.fn(),
    tagUsage: vi.fn(),
  },
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

import { notify, notifyError } from "@/components/ui/toast";
import {
  scheduleKeys,
  sessionKeys,
  topicKeys,
} from "@/features/events/api/query-keys";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import {
  useTagLifecycle,
  useTagUsage,
} from "@/features/expertise/api/use-tag-lifecycle";
import { artifactKeys } from "@/features/knowledge/api/query-keys";
import { learningKeys } from "@/features/learning/api/query-keys";
import { ApiError } from "@/lib/api-client";

const gateway = vi.mocked(httpExpertiseGateway);
const notifyMock = vi.mocked(notify);
const notifyErrorMock = vi.mocked(notifyError);

let client: QueryClient;
let invalidateSpy: ReturnType<typeof vi.spyOn>;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  invalidateSpy = vi.spyOn(client, "invalidateQueries");
});

describe("useTagLifecycle", () => {
  it("renames a tag and tells the curator, with no undo offered", async () => {
    // given
    gateway.renameTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("kafak", "kafka");

    // then
    await waitFor(() => expect(gateway.renameTag.mock.calls).toHaveLength(1));
    expect(gateway.renameTag.mock.calls[0]).toEqual([
      "guild",
      "kafak",
      "kafka",
    ]);
    await waitFor(() => expect(notifyMock).toHaveBeenCalledTimes(1));
    expect(notifyMock).toHaveBeenCalledWith("Renamed kafak to kafka");
    expect(notifyMock.mock.calls[0]).toHaveLength(1);
  });

  it("deletes a tag and tells the curator, with no undo offered", async () => {
    // given
    gateway.deleteTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.deleteTag("misc");

    // then
    await waitFor(() => expect(gateway.deleteTag.mock.calls).toHaveLength(1));
    expect(gateway.deleteTag.mock.calls[0]).toEqual(["guild", "misc"]);
    await waitFor(() => expect(notifyMock).toHaveBeenCalledTimes(1));
    expect(notifyMock).toHaveBeenCalledWith("Deleted misc");
    expect(notifyMock.mock.calls[0]).toHaveLength(1);
  });

  it("invalidates the library too, because artifact chips changed", async () => {
    // given
    gateway.deleteTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.deleteTag("misc");

    // then
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: artifactKeys.community("guild"),
      }),
    );
  });

  it("invalidates the Learning page after a rename, because areas to learn changed", async () => {
    // given
    gateway.renameTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("kafak", "kafka");

    // then
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: learningKeys.community("guild"),
      }),
    );
  });

  it("invalidates the Learning page after a delete, because areas to learn changed", async () => {
    // given
    gateway.deleteTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.deleteTag("misc");

    // then
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: learningKeys.community("guild"),
      }),
    );
  });

  it("invalidates the events caches too, because topic and slot tags changed", async () => {
    // given
    gateway.renameTag.mockResolvedValue(undefined);
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("kafak", "kafka");

    // then - the Topics and Schedule pages would otherwise serve the old tag for 30s
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: topicKeys.community("guild"),
      }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: sessionKeys.all,
    });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: scheduleKeys.all,
    });
  });

  it("reports a failure toast when the rename rejects", async () => {
    // given
    gateway.renameTag.mockRejectedValue(new Error("merged"));
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("kafak", "kafka");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith("Couldn't rename that tag."),
    );
  });

  it("reports a failure toast when the delete rejects", async () => {
    // given
    gateway.deleteTag.mockRejectedValue(new Error("merged"));
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.deleteTag("misc");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith("Couldn't delete that tag."),
    );
  });

  it("says why the backend refused the rename, since neither verb can be undone", async () => {
    // given - a name the dialog's canonical list cannot know is a merged variant
    gateway.renameTag.mockRejectedValue(
      new ApiError(409, "detail", "RENAME_TARGET_MERGED"),
    );
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("streams", "kafak");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith(
        expect.stringMatching(/merged/i),
      ),
    );
  });

  it("says why the backend refused the delete", async () => {
    // given
    gateway.deleteTag.mockRejectedValue(
      new ApiError(409, "detail", "TAG_MERGED"),
    );
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.deleteTag("kafka");

    // then
    await waitFor(() =>
      expect(notifyErrorMock).toHaveBeenCalledWith(
        expect.stringMatching(/split/i),
      ),
    );
  });

  it("is pending while a rename is in flight", async () => {
    // given
    gateway.renameTag.mockReturnValue(new Promise(() => undefined));
    const { result } = renderHook(() => useTagLifecycle("guild"), { wrapper });

    // when
    result.current.renameTag("kafak", "kafka");

    // then
    await waitFor(() => expect(result.current.isPending).toBe(true));
  });
});

describe("useTagUsage", () => {
  it("reads what the tag is stamped on", async () => {
    // given
    gateway.tagUsage.mockResolvedValue({
      artifacts: 3,
      topics: 2,
      talks: 0,
      declarations: 1,
      learningIntents: 2,
    });

    // when
    const { result } = renderHook(() => useTagUsage("guild", "misc"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      artifacts: 3,
      topics: 2,
      talks: 0,
      declarations: 1,
      learningIntents: 2,
    });
    expect(gateway.tagUsage.mock.calls[0]).toEqual(["guild", "misc"]);
  });

  it("asks nothing while no tag is picked", () => {
    // when
    const { result } = renderHook(() => useTagUsage("guild", null), {
      wrapper,
    });

    // then
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.tagUsage.mock.calls).toHaveLength(0);
  });
});
