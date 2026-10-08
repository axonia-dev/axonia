import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

describe("useAnsweredAfterFailing", () => {
  it("stays false for a read that answers on its first try", () => {
    // given
    const { result, rerender } = renderHook(
      ({ failed }) => useAnsweredAfterFailing(failed),
      { initialProps: { failed: false } },
    );

    // when
    rerender({ failed: false });

    // then
    expect(result.current).toBe(false);
  });

  it("stays false while the read is failed", () => {
    // given
    const { result, rerender } = renderHook(
      ({ failed }) => useAnsweredAfterFailing(failed),
      { initialProps: { failed: false } },
    );

    // when
    rerender({ failed: true });

    // then
    expect(result.current).toBe(false);
  });

  it("turns true once the read answers after failing", () => {
    // given
    const { result, rerender } = renderHook(
      ({ failed }) => useAnsweredAfterFailing(failed),
      { initialProps: { failed: false } },
    );
    rerender({ failed: true });

    // when
    rerender({ failed: false });

    // then
    expect(result.current).toBe(true);
  });

  it("turns true when a read mounted failed answers", () => {
    // given
    const { result, rerender } = renderHook(
      ({ failed }) => useAnsweredAfterFailing(failed),
      { initialProps: { failed: true } },
    );

    // when
    rerender({ failed: false });

    // then
    expect(result.current).toBe(true);
  });
});
