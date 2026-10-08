import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  className,
}: Readonly<PaginationProps>) {
  return (
    <nav
      aria-label="Pagination"
      className={cn("flex items-center justify-between gap-4", className)}
    >
      <Button
        variant="secondary"
        size="sm"
        isDisabled={page <= 0}
        onPress={() => onPageChange(page - 1)}
      >
        Previous
      </Button>
      <span className="text-muted-foreground text-sm tabular-nums">
        Page {page + 1} of {totalPages}
      </span>
      <Button
        variant="secondary"
        size="sm"
        isDisabled={page >= totalPages - 1}
        onPress={() => onPageChange(page + 1)}
      >
        Next
      </Button>
    </nav>
  );
}
