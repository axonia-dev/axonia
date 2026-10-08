import { cn } from "@/lib/cn";

interface FormErrorProps {
  /** Truthy ⇒ render the message. Typically a mutation error object. */
  error: unknown;
  /** Overrides the default copy (e.g. a context-specific message). */
  message?: string;
  className?: string;
}

const DEFAULT_MESSAGE = "Something went wrong. Please try again.";

export function FormError({
  error,
  message,
  className,
}: Readonly<FormErrorProps>) {
  if (!error) {
    return null;
  }
  return (
    <p role="alert" className={cn("text-danger text-sm", className)}>
      {message ?? DEFAULT_MESSAGE}
    </p>
  );
}
