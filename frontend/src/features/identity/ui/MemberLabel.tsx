import type { MemberStatus } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

/**
 * The one place a person's name is rendered with their standing. A deactivated member keeps their
 * name and wears the reason beside it, the way Jira marks a departed assignee; an erased member has
 * no name left to show, so the backend's neutral label carries alone in muted italics.
 *
 * `className` is applied ahead of this component's own colour utility in both branches, so a
 * caller-supplied text-colour class can never win a `tailwind-merge` conflict and silently erase
 * the muted/italic ERASED treatment (or a muted DEACTIVATED name). Non-colour utilities (size,
 * weight, margin) are unaffected and compose normally.
 */
export function MemberLabel({
  name,
  status,
  className,
}: Readonly<{ name: string; status: MemberStatus; className?: string }>) {
  if (status === "ERASED") {
    return (
      <span className={cn(className, "text-muted-foreground italic")}>
        {name}
      </span>
    );
  }
  return (
    <span
      className={cn(
        className,
        status === "DEACTIVATED" && "text-muted-foreground",
      )}
    >
      {name}
      {status === "DEACTIVATED" && (
        <span className="ml-1.5 text-xs">(Deactivated)</span>
      )}
    </span>
  );
}
