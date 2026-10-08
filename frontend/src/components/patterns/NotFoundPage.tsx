import { Compass } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/Button";

interface NotFoundPageProps {
  /** Where the primary action sends the user. Defaults to the app root. */
  homeTo?: string;
  /** Label for the primary action. */
  homeLabel?: string;
}

/**
 * The catch-all destination for any route that does not match. Rendered both at
 * the top level and nested inside the community shell (with a community-scoped
 * home target), so an unknown or stale deep link lands somewhere sane instead of
 * on a blank page.
 */
export function NotFoundPage({
  homeTo = "/",
  homeLabel = "Go home",
}: Readonly<NotFoundPageProps>) {
  const navigate = useNavigate();
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <Compass
        aria-hidden
        className="text-muted-foreground/50 h-16 w-16"
        strokeWidth={1.5}
      />
      <div className="flex flex-col gap-1">
        <h1 className="font-display text-foreground text-2xl font-semibold">
          Page not found
        </h1>
        <p className="text-muted-foreground max-w-sm text-sm">
          This page doesn’t exist, or it may have moved.
        </p>
      </div>
      <Button
        onPress={() => {
          void navigate(homeTo);
        }}
      >
        {homeLabel}
      </Button>
    </div>
  );
}
