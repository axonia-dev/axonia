import { ApiError } from "@/lib/api-client";

const MESSAGES: Record<string, string> = {
  NO_SELF_LOVE:
    "A contribution cannot be loved by one of its own contributors.",
  ALREADY_LOVED: "This is already loved.",
  REACTION_LIMIT_REACHED:
    "The limit of custom reactions is reached. Removing one makes room for another.",
};

export function recognitionErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const message = MESSAGES[error.code];
    if (message) {
      return message;
    }
  }
  return "Something went wrong. Please try again.";
}
