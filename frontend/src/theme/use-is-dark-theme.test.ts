import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { useIsDarkTheme } from "@/theme/use-is-dark-theme";

afterEach(() => document.documentElement.classList.remove("theme-dark"));

describe("useIsDarkTheme", () => {
  it("follows the theme class on the document", async () => {
    const { result } = renderHook(() => useIsDarkTheme());
    expect(result.current).toBe(false);

    await act(async () => {
      document.documentElement.classList.add("theme-dark");
      await Promise.resolve();
    });

    expect(result.current).toBe(true);
  });
});
