/// <reference types="node" />
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useRoleWriteQueue } from "@/features/admin/ui/use-role-write-queue";

function deferred() {
  let resolve!: () => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<void>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("useRoleWriteQueue", () => {
  it("runs tasks strictly one after another", async () => {
    const { result } = renderHook(() => useRoleWriteQueue());
    const first = deferred();
    const order: string[] = [];

    result.current.enqueue(async () => {
      order.push("first:start");
      await first.promise;
      order.push("first:end");
    });
    result.current.enqueue(() => {
      order.push("second:start");
      return Promise.resolve();
    });

    await Promise.resolve();
    expect(order).toEqual(["first:start"]);

    first.resolve();
    await new Promise((r) => setTimeout(r, 0));
    expect(order).toEqual(["first:start", "first:end", "second:start"]);
  });

  it("keeps draining after a task rejects", async () => {
    const { result } = renderHook(() => useRoleWriteQueue());
    const order: string[] = [];

    result.current.enqueue(() => Promise.reject(new Error("refused")));
    result.current.enqueue(() => {
      order.push("after");
      return Promise.resolve();
    });

    await new Promise((r) => setTimeout(r, 0));
    expect(order).toEqual(["after"]);
  });

  it("does not leave an unhandled rejection when the last task rejects", async () => {
    const { result } = renderHook(() => useRoleWriteQueue());
    let caughtRejection: Error | null = null;

    const handler = (reason: unknown) => {
      if (reason instanceof Error) {
        caughtRejection = reason;
      }
    };
    process.on("unhandledRejection", handler);

    try {
      result.current.enqueue(() => Promise.reject(new Error("final reject")));
      await new Promise((r) => setTimeout(r, 0));
      await new Promise((r) => setTimeout(r, 0));
      expect(caughtRejection).toBeNull();
    } finally {
      process.off("unhandledRejection", handler);
    }
  });
});
