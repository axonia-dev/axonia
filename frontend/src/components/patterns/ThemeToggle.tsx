import { Moon, Sun } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { useTheme } from "@/theme/ThemeProvider";

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const nextLabel =
    theme === "dark" ? "Switch to light theme" : "Switch to dark theme";

  return (
    <Button
      variant="ghost"
      aria-label={nextLabel}
      onPress={toggleTheme}
      className="size-9 p-0"
    >
      {theme === "dark" ? (
        <Sun size={18} aria-hidden />
      ) : (
        <Moon size={18} aria-hidden />
      )}
    </Button>
  );
}
