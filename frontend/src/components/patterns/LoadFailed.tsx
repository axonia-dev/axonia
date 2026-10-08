import { useRef } from "react";

import { Button } from "@/components/ui/Button";
import type { RetryableRead } from "@/lib/failed-to-load";
import { useReportsDroppedFocus } from "@/lib/use-answered-after-failing";

interface LoadFailedProps {
  message: string;
  query: RetryableRead;
  /** Under a page's own header, as the app's other page errors sit, rather than filling the
   *  screen in place of a whole surface. */
  inline?: boolean;
}

/** What shows in place of what could not load, given the query that failed. It must not read
 *  that query through a hook of its own: a new observer refetches a failed query on mount. */
export function LoadFailed({
  message,
  query,
  inline = false,
}: Readonly<LoadFailedProps>) {
  const root = useRef<HTMLDivElement>(null);
  useReportsDroppedFocus(root);
  const retry = (
    <Button
      variant={inline ? "outline" : "primary"}
      size={inline ? "sm" : "md"}
      onPress={() => void query.refetch()}
      isPending={query.isFetching}
    >
      Try again
    </Button>
  );
  if (inline) {
    return (
      <div ref={root} className="flex flex-col items-start gap-3">
        <p role="alert" className="text-danger text-sm">
          {message}
        </p>
        {retry}
      </div>
    );
  }
  return (
    <div
      ref={root}
      className="bg-background text-foreground flex min-h-full flex-col items-center justify-center gap-4 px-6 text-center"
    >
      <p role="alert" className="text-muted-foreground">
        {message}
      </p>
      {retry}
    </div>
  );
}
