import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { useSpeakerSuggestions } from "@/features/expertise/api/use-speaker-suggestions";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { userHue } from "@/lib/user-hue";

interface Props {
  slug: string;
  tags: string[];
}

function SuggestionChip({
  practitioner,
}: Readonly<{ practitioner: Practitioner }>) {
  const name = practitioner.displayName ?? practitioner.userId;
  const hue = userHue(practitioner.userId);
  return (
    <span className="border-border bg-surface flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs">
      <MemberAvatar
        name={name}
        hue={hue}
        subject={practitioner.userId}
        src={practitioner.avatarUrl}
        shape="circle"
        className="size-4 text-[8px]"
      />
      <span className="text-foreground">{name}</span>
    </span>
  );
}

export function SpeakerSuggestions({ slug, tags }: Readonly<Props>) {
  const isOrganizer = useIsOrganizer(slug);
  const { data } = useSpeakerSuggestions(slug, tags);

  if (!isOrganizer || tags.length === 0 || !data || data.length === 0) {
    return null;
  }

  return (
    <div className="mt-1 flex flex-wrap items-center gap-1.5">
      <span className="text-muted-foreground text-xs">Suggested speakers:</span>
      {data.map((p) => (
        <SuggestionChip key={p.userId} practitioner={p} />
      ))}
    </div>
  );
}
