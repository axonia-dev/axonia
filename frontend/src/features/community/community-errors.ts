import type { ChatPlatform } from "@/features/community/domain/chat-destination";
import { CHAT_PLATFORM_COPY } from "@/features/community/domain/chat-destination";
import { ApiError } from "@/lib/api-client";

const MESSAGES: Record<string, string> = {
  ALREADY_MEMBER: "Already a member of this community.",
  LAST_ORGANIZER_CANNOT_LEAVE:
    "This is the last organizer. Promote someone before leaving.",
  LAST_ORGANIZER_CANNOT_BE_DEMOTED:
    "Promote another organizer first. A community always needs one.",
  COMMUNITY_ARCHIVED: "This community is archived and read-only.",
  SLUG_TAKEN: "A community with a similar name already exists. Try another.",
  KIND_EXISTS: "That kind already exists in the vocabulary.",
  KIND_IN_USE:
    "Communities still use this kind. Choose where to move them first.",
  INVALID_REASSIGNMENT: "Pick a valid kind to move those communities to.",
  INVALID_CHAT_WEBHOOK_URL:
    "That does not look like a webhook URL from the selected chat tool.",
  CHAT_PLATFORM_REQUIRED: "Choose which chat tool this webhook belongs to.",
  CHAT_DESTINATION_LIMIT:
    "A community can post to at most ten chat destinations. Remove one first.",
  CHAT_DESTINATION_NAME_TAKEN:
    "This community already has a destination with that name.",
  CHAT_DESTINATION_URL_UNPARSEABLE:
    "This destination's stored URL is no longer valid. Update it before sending a test post.",
  CHAT_SEND_FAILED:
    "The team's chat tool refused the test post. Check that the webhook is still valid in Slack or Teams - this failure happened on their end, not axonia's.",
};

/**
 * A refused webhook URL is the one error whose message depends on what the
 * leader was trying to configure, so it is resolved against the platform they
 * chose rather than from the code alone: telling someone pasting a Teams URL
 * that it "does not look like a Slack or Teams URL" names neither the mistake
 * nor the fix.
 */
export function chatDestinationErrorMessage(
  error: unknown,
  platform: ChatPlatform,
): string {
  if (error instanceof ApiError && error.code === "INVALID_CHAT_WEBHOOK_URL") {
    return CHAT_PLATFORM_COPY[platform].invalidUrl;
  }
  return communityErrorMessage(error);
}

export function communityErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const message = MESSAGES[error.code];
    if (message) {
      return message;
    }
  }
  return "Something went wrong. Please try again.";
}
