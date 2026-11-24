interface Waiter<V> {
  resolve: (value: V) => void;
  reject: (reason: unknown) => void;
}

/**
 * Turns many single-id reads into few batched ones. Every `load(id)` made in the same tick joins
 * one batch, sent once the tick ends, in chunks of at most `maxBatch` distinct ids; each caller
 * gets the value for its own id. So a page of twenty cards that each read their own state makes
 * one request, while every caller keeps reading, caching and invalidating per id.
 *
 * An id the batch answers nothing for rejects, as a single read of a missing item would.
 */
export function createBatchLoader<V>(
  loadMany: (ids: number[]) => Promise<Map<number, V>>,
  maxBatch: number,
): (id: number) => Promise<V> {
  let pending = new Map<number, Waiter<V>[]>();

  function settle(ids: number[], waiters: Map<number, Waiter<V>[]>) {
    loadMany(ids).then(
      (values) => {
        for (const id of ids) {
          const value = values.get(id);
          for (const waiter of waiters.get(id) ?? []) {
            if (value === undefined) {
              waiter.reject(new Error(`No value loaded for ${id}`));
            } else {
              waiter.resolve(value);
            }
          }
        }
      },
      (error: unknown) => {
        for (const id of ids) {
          for (const waiter of waiters.get(id) ?? []) {
            waiter.reject(error);
          }
        }
      },
    );
  }

  function flush() {
    const waiters = pending;
    pending = new Map();
    const ids = [...waiters.keys()];
    for (let start = 0; start < ids.length; start += maxBatch) {
      settle(ids.slice(start, start + maxBatch), waiters);
    }
  }

  return (id) =>
    new Promise<V>((resolve, reject) => {
      if (pending.size === 0) {
        setTimeout(flush, 0);
      }
      const waiters = pending.get(id) ?? [];
      waiters.push({ resolve, reject });
      pending.set(id, waiters);
    });
}
