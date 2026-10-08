import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";

import { useThemePaint } from "@/features/expertise/ui/use-theme-paint";

function Painted() {
  return <span data-testid="painted">{useThemePaint()}</span>;
}

afterEach(() => {
  document.documentElement.className = "";
  document.documentElement.style.removeProperty("--primary");
});

it("answers what the page is painted in", () => {
  document.documentElement.className = "theme-dark";
  render(<Painted />);

  expect(screen.getByTestId("painted")).toHaveTextContent("theme-dark");
});

// WebGL reads no cascade, so without this a reader who switches theme keeps the old map until the page is reloaded.
it("follows the page when the theme is switched under it", async () => {
  document.documentElement.className = "theme-dark";
  render(<Painted />);

  document.documentElement.className = "";

  await waitFor(() =>
    expect(screen.getByTestId("painted")).toHaveTextContent(""),
  );
});

/**
 * A community publishes its accent as `--primary` on the same element, from an effect in the shell above the map.
 * Effects run child first, so the map's layers are built before that accent lands: watched only for its class, the
 * drawing kept the product's own pink until something else happened to rebuild it.
 */
it("follows the accent the community publishes, not only the theme", async () => {
  render(<Painted />);
  const before = screen.getByTestId("painted").textContent;

  document.documentElement.style.setProperty("--primary", "#abcdef");

  await waitFor(() =>
    expect(screen.getByTestId("painted").textContent).not.toBe(before),
  );
});
