import { ApiError } from "@/lib/api-client";

const MESSAGES: Record<string, string> = {
  CONTRIBUTOR_NOT_FOUND: "That person is not a member of this community.",
  CONTRIBUTOR_AMBIGUOUS:
    "Several members match that name. Pick one from the list.",
};

const GENERIC_MESSAGE = "Something went wrong. Please try again.";

/** A codeless 403 when crediting another member: the Credit contributions right closed mid-session. */
const CREDIT_CLOSED_MESSAGE = "Crediting contributions is no longer open here.";

function codedMessage(error: unknown): string | undefined {
  return error instanceof ApiError && error.code
    ? MESSAGES[error.code]
    : undefined;
}

/** A capture or contributor change refused over whom it credits. */
export function creditErrorMessage(error: unknown): string {
  const forbidden = error instanceof ApiError && error.status === 403;
  return (
    codedMessage(error) ?? (forbidden ? CREDIT_CLOSED_MESSAGE : GENERIC_MESSAGE)
  );
}

/** A refused capture that credits nobody else, so crediting is never to blame. */
export function captureErrorMessage(error: unknown): string {
  return codedMessage(error) ?? GENERIC_MESSAGE;
}
