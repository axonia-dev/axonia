import { describe, expect, it, vi } from "vitest";

import { createBatchLoader } from "@/lib/batch-loader";

function doubling() {
  return vi.fn((ids: number[]) =>
    Promise.resolve(new Map(ids.map((id) => [id, id * 2]))),
  );
}

describe("createBatchLoader", () => {
  it("answers every read made in the same tick from one batch", async () => {
    // given
    const loadMany = doubling();
    const load = createBatchLoader(loadMany, 100);

    // when
    const values = await Promise.all([load(1), load(2), load(3)]);

    // then
    expect(values).toEqual([2, 4, 6]);
    expect(loadMany).toHaveBeenCalledTimes(1);
    expect(loadMany).toHaveBeenCalledWith([1, 2, 3]);
  });

  it("asks for an id read twice in the same tick only once, and answers both", async () => {
    // given
    const loadMany = doubling();
    const load = createBatchLoader(loadMany, 100);

    // when
    const values = await Promise.all([load(7), load(7)]);

    // then
    expect(values).toEqual([14, 14]);
    expect(loadMany).toHaveBeenCalledWith([7]);
  });

  it("starts a new batch for reads made after the previous one was sent", async () => {
    // given
    const loadMany = doubling();
    const load = createBatchLoader(loadMany, 100);

    // when
    await load(1);
    await load(2);

    // then
    expect(loadMany.mock.calls).toEqual([[[1]], [[2]]]);
  });

  it("splits a batch larger than the cap into chunks", async () => {
    // given
    const loadMany = doubling();
    const load = createBatchLoader(loadMany, 2);

    // when
    await Promise.all([load(1), load(2), load(3)]);

    // then
    expect(loadMany.mock.calls).toEqual([[[1, 2]], [[3]]]);
  });

  it("rejects a read the batch answers nothing for, and still answers the others", async () => {
    // given
    const load = createBatchLoader(
      (ids) =>
        Promise.resolve(
          new Map(ids.filter((id) => id !== 2).map((id) => [id, id])),
        ),
      100,
    );

    // when
    const [found, missing] = await Promise.allSettled([load(1), load(2)]);

    // then
    expect(found).toEqual({ status: "fulfilled", value: 1 });
    expect(missing.status).toBe("rejected");
  });

  it("rejects every read of a batch that failed", async () => {
    // given
    const failure = new Error("offline");
    const load = createBatchLoader(() => Promise.reject(failure), 100);

    // when
    const results = await Promise.allSettled([load(1), load(2)]);

    // then
    expect(results).toEqual([
      { status: "rejected", reason: failure },
      { status: "rejected", reason: failure },
    ]);
  });
});
