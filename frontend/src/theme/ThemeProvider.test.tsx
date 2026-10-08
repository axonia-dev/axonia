import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import {
  applyTheme,
  resolveInitialTheme,
  ThemeProvider,
  useTheme,
} from "@/theme/ThemeProvider";

describe("applyTheme", () => {
  afterEach(() => {
    document.documentElement.className = "";
  });

  it("adds the theme-dark class for dark and removes it for light", () => {
    // when / then
    applyTheme("dark");
    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      true,
    );
    applyTheme("light");
    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      false,
    );
  });
});

describe("resolveInitialTheme", () => {
  it("honors a stored theme", () => {
    expect(resolveInitialTheme("light", true)).toBe("light");
    expect(resolveInitialTheme("dark", false)).toBe("dark");
  });

  it("falls back to system preference when nothing stored", () => {
    expect(resolveInitialTheme(null, true)).toBe("dark");
  });

  it("defaults to light (light-first) when nothing stored and no dark preference", () => {
    expect(resolveInitialTheme(null, false)).toBe("light");
  });

  it("ignores invalid stored values", () => {
    expect(resolveInitialTheme("purple", false)).toBe("light");
  });
});

function ThemeProbe() {
  const { theme, toggleTheme } = useTheme();
  return (
    <button type="button" onClick={toggleTheme}>
      theme:{theme}
    </button>
  );
}

describe("ThemeProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = "";
  });
  afterEach(() => {
    localStorage.clear();
    document.documentElement.className = "";
  });

  it("defaults to light and applies no theme-dark class", () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByRole("button")).toHaveTextContent("theme:light");
    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      false,
    );
  });

  it("toggles to dark, sets the class, and persists", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button")).toHaveTextContent("theme:dark");
    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      true,
    );
    expect(localStorage.getItem("axonia-theme")).toBe("dark");
  });

  it("throws when useTheme is used outside the provider", () => {
    function Orphan() {
      useTheme();
      return null;
    }
    expect(() => render(<Orphan />)).toThrow(/ThemeProvider/);
  });
});
