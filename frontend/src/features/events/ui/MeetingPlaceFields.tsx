import { KeyRound, Link2, MapPin } from "lucide-react";
import { useId } from "react";

import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import type { MeetingPlaceDraft } from "@/features/events/domain/meeting-place";

interface Props {
  value: MeetingPlaceDraft;
  onChange: (draft: MeetingPlaceDraft) => void;
  /** A rule the three fields cannot show, such as what leaving all of them empty means. Only ever
   *  a rule: a description that restates the labels above it is noise. */
  description?: string;
}

/**
 * Where a session meets, as the one block every form mounts. The three fields are one value, so
 * they are edited together and reported together: a caller that could set the join link without
 * seeing the location beside it would have no way to honour the all-or-nothing rule.
 *
 * Order is fixed - location, join link, joining details - and matches the order the resolved place
 * is read back in, so an organizer checking their own entry scans the same three rows twice.
 *
 * The three fields are wrapped in a named group carrying the description, because the description
 * is about the block and not about any one field. Hung off the last field instead, the inheritance
 * rule was announced only to somebody who reached the joining details, and read on screen as a note
 * about a passcode - which is the one thing it is not about.
 */
export function MeetingPlaceFields({
  value,
  onChange,
  description,
}: Readonly<Props>) {
  const descriptionId = useId();
  return (
    <div
      role="group"
      aria-label="Meeting place"
      {...(description !== undefined
        ? { "aria-describedby": descriptionId }
        : {})}
      className="flex flex-col gap-4"
    >
      <TextInput
        label="Location"
        icon={<MapPin aria-hidden className="h-4 w-4" />}
        value={value.location}
        onChange={(location) => onChange({ ...value, location })}
        placeholder="Room 4B, Gasperich"
      />
      <TextInput
        label="Join link"
        type="url"
        icon={<Link2 aria-hidden className="h-4 w-4" />}
        value={value.joinUrl}
        onChange={(joinUrl) => onChange({ ...value, joinUrl })}
        placeholder="https://teams.microsoft.com/..."
      />
      <TextArea
        label="Joining details"
        icon={<KeyRound aria-hidden className="h-4 w-4" />}
        rows={3}
        value={value.joiningDetails}
        onChange={(joiningDetails) => onChange({ ...value, joiningDetails })}
        placeholder="Meeting ID, passcode, dial-in"
      />
      {description !== undefined && (
        <p id={descriptionId} className="text-muted-foreground text-xs">
          {description}
        </p>
      )}
    </div>
  );
}
