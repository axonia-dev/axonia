import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PageHeader } from "@/components/patterns/PageHeader";
import { cn } from "@/lib/cn";
import type { RetryableRead } from "@/lib/failed-to-load";

interface PageLoadFailedProps {
  /** The page's own heading, kept on screen so the member still knows where they are. */
  title?: string | undefined;
  message: string;
  query: RetryableRead;
  /** For a page whose own sections sit further apart than the default `gap-6`. */
  className?: string;
}

/** A whole page whose read failed: its header, then what could not load and a way to ask again. */
export function PageLoadFailed({
  title,
  message,
  query,
  className,
}: Readonly<PageLoadFailedProps>) {
  return (
    <div className={cn("flex flex-col gap-6", className)}>
      {title !== undefined && <PageHeader title={title} />}
      <LoadFailed inline message={message} query={query} />
    </div>
  );
}
