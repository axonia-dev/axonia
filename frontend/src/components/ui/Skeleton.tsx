import { cn } from "@/lib/cn";

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className }: Readonly<SkeletonProps>) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "bg-surface-sunken rounded-md motion-safe:animate-pulse",
        className,
      )}
    />
  );
}
