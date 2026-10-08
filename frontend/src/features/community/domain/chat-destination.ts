import { z } from "zod";

const chatPlatformSchema = z.enum(["SLACK", "TEAMS"]);

export type ChatPlatform = z.infer<typeof chatPlatformSchema>;

export const chatDestinationSchema = z.object({
  id: z.number(),
  name: z.string(),
  platform: chatPlatformSchema.nullable(),
  maskedUrl: z.string(),
  broken: z.boolean(),
  lastPostedAt: z.string().nullable().optional(),
  lastFailureAt: z.string().nullable().optional(),
  lastError: z.string().nullable().optional(),
});

export const chatDestinationListSchema = z.array(chatDestinationSchema);

export type ChatDestination = z.infer<typeof chatDestinationSchema>;

export interface ChatDestinationInput {
  name: string;
  platform: ChatPlatform;
  webhookUrl: string | null;
}

const CHAT_PLATFORM_LABELS: Record<ChatPlatform, string> = {
  SLACK: "Slack",
  TEAMS: "Teams",
};

interface ChatPlatformCopy {
  /** Named in full where the leader is choosing their tool, rather than the
   *  compact label a configured row's status line uses. */
  choice: string;
  urlPlaceholder: string;
  urlHint: string;
  /** Shown when the backend refuses the URL: the one message that can say what
   *  a valid URL looks like, which only knowing the chosen platform allows. */
  invalidUrl: string;
}

/**
 * Everything the dialog says differs by platform, because the two products hand
 * a leader entirely different-looking URLs from entirely different places. One
 * shared hint covering both is what let someone configuring Teams read a Slack
 * placeholder and conclude they had the wrong URL.
 */
export const CHAT_PLATFORM_COPY: Record<ChatPlatform, ChatPlatformCopy> = {
  SLACK: {
    choice: "Slack",
    urlPlaceholder: "https://hooks.slack.com/services/...",
    urlHint: "Slack shows this when an incoming webhook is added to a channel.",
    invalidUrl:
      "That is not a Slack incoming webhook URL. Slack's own starts with https://hooks.slack.com/services/.",
  },
  TEAMS: {
    choice: "Microsoft Teams",
    urlPlaceholder: "https://...environment.api.powerplatform.com/...",
    urlHint:
      "In Power Automate, open the flow's Teams webhook trigger and copy the HTTP POST URL it shows.",
    invalidUrl:
      "That is not a Microsoft Teams webhook URL. In Power Automate, open the flow's Teams webhook trigger and copy the HTTP POST URL it shows.",
  },
};

export const CHAT_PLATFORM_OPTIONS = (
  Object.keys(CHAT_PLATFORM_COPY) as ChatPlatform[]
).map((id) => ({ id, label: CHAT_PLATFORM_COPY[id].choice }));

const TIMESTAMP_FORMAT = new Intl.DateTimeFormat("en", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatChatTimestamp(iso: string): string {
  return TIMESTAMP_FORMAT.format(new Date(iso));
}

/** The row's own second line: the platform it belongs to, and when it last worked. */
export function destinationDetail(destination: ChatDestination): string {
  const platform =
    destination.platform !== null
      ? CHAT_PLATFORM_LABELS[destination.platform]
      : "Chat";
  if (destination.lastPostedAt == null || destination.broken) {
    return platform;
  }
  return `${platform}, posted ${formatChatTimestamp(destination.lastPostedAt)}`;
}
