import { useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useAddSpeaker } from "@/features/events/api/use-topic-speakers";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { useSpeakerSuggestions } from "@/features/expertise/api/use-speaker-suggestions";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { userHue } from "@/lib/user-hue";

interface Props {
  slug: string;
  topicId: number;
  tags: string[];
}

function SuggestionChip({
  practitioner,
  isPending,
  onPick,
}: Readonly<{
  practitioner: Practitioner;
  isPending: boolean;
  onPick: () => void;
}>) {
  const name = practitioner.displayName ?? practitioner.userId;
  const hue = userHue(practitioner.userId);
  return (
    <button
      type="button"
      aria-label={`Add ${name} as speaker`}
      disabled={isPending}
      onClick={onPick}
      className="border-border bg-surface hover:border-primary hover:bg-primary/10 focus-visible:ring-focus-ring focus-visible:ring-offset-background flex cursor-pointer items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs transition-colors focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50"
    >
      <MemberAvatar
        name={name}
        hue={hue}
        subject={practitioner.userId}
        src={practitioner.avatarUrl}
        shape="circle"
        className="size-4 text-[8px]"
      />
      <span className="text-foreground">{name}</span>
    </button>
  );
}

const NOT_MATCHED_MESSAGE = "That person could not be matched.";

function refusalOf(
  notMatched: boolean,
  addSpeaker: ReturnType<typeof useAddSpeaker>,
): string | null {
  if (notMatched) {
    return NOT_MATCHED_MESSAGE;
  }
  return addSpeaker.isError ? eventsErrorMessage(addSpeaker.error) : null;
}

/** Who the community's expertise suggests for a request; one click names them its speaker. */
export function SpeakerSuggestions({ slug, topicId, tags }: Readonly<Props>) {
  const isOrganizer = useIsOrganizer(slug);
  const { data } = useSpeakerSuggestions(slug, tags);
  const addSpeaker = useAddSpeaker(slug, topicId);
  const [notMatched, setNotMatched] = useState(false);

  if (!isOrganizer || tags.length === 0 || !data || data.length === 0) {
    return null;
  }
  const refusal = refusalOf(notMatched, addSpeaker);

  return (
    <div className="mt-1 flex flex-wrap items-center gap-1.5">
      <span className="text-muted-foreground text-xs">Suggested speakers:</span>
      {data.map((p) => (
        <SuggestionChip
          key={p.userId}
          practitioner={p}
          isPending={addSpeaker.isPending}
          onPick={() => {
            setNotMatched(false);
            addSpeaker.mutate(p.userId, {
              onSuccess: (result) => setNotMatched(!result.assigned),
            });
          }}
        />
      ))}
      {refusal && (
        <p role="alert" className="text-danger w-full text-xs">
          {refusal}
        </p>
      )}
    </div>
  );
}
