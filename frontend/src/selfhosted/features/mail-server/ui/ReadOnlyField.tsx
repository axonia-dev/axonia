import { type ReactNode, useId } from "react";

type Props = Readonly<{
  label: string;
  value: ReactNode;
  lockedBy?: string;
  action?: ReactNode;
  error?: string;
}>;

/** A value the reader cannot type over: a field the deployment sets, or the stored password.
 *  Text, never a disabled or masked input, so nothing drawn here can be submitted back. */
export function ReadOnlyField({
  label,
  value,
  lockedBy,
  action,
  error,
}: Props) {
  const labelId = useId();
  const errorId = useId();
  return (
    <div
      role="group"
      aria-labelledby={labelId}
      {...(error === undefined ? {} : { "aria-describedby": errorId })}
      className="flex min-w-0 flex-col gap-1"
    >
      <span id={labelId} className="text-foreground text-sm font-medium">
        {label}
      </span>
      <div className="flex items-center gap-2">
        <p className="border-border bg-surface-sunken text-foreground min-w-0 flex-1 rounded-md border px-3 py-2 text-sm wrap-anywhere">
          {value}
        </p>
        {action}
      </div>
      {lockedBy !== undefined && (
        <p className="text-muted-foreground text-xs">
          Set by <code className="font-mono">{lockedBy}</code>
        </p>
      )}
      {error !== undefined && (
        <p id={errorId} role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
    </div>
  );
}
