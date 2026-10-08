import type { SyntheticEvent } from "react";
import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DateTimePicker } from "@/components/ui/DateTimePicker";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { DisclosureSection } from "@/components/ui/DisclosureSection";
import { TextInput } from "@/components/ui/TextInput";
import { useSectionTypes } from "@/features/events/api/use-section-types";
import { useCreateOneOff } from "@/features/events/api/use-sessions";
import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";
import {
  draftFromPlace,
  EMPTY_PLACE,
  placeFromDraft,
  placeIsEmpty,
} from "@/features/events/domain/meeting-place";
import { type ZonedParts } from "@/features/events/domain/zoned-instant";
import { MeetingPlaceFields } from "@/features/events/ui/MeetingPlaceFields";
import { MemberZoneSpread } from "@/features/events/ui/MemberZoneSpread";
import {
  placeIsBlank,
  placeSummary,
} from "@/features/events/ui/series-form-summaries";
import { StructureSection } from "@/features/events/ui/StructureSection";
import { viewerZone } from "@/lib/zones";

/** A one-off belongs to no series, so the clock it is typed on is the viewer's own, and the
 *  server honours the zone this form states. An hour is not optional: a session with no series is
 *  refused without one. */
function seededWhen(defaultDate: string | undefined): ZonedParts {
  return defaultDate === undefined
    ? { date: "", time: "" }
    : { date: defaultDate, time: "09:00" };
}

export function OneOffSessionForm({
  slug,
  onSaved,
  defaultDate,
}: Readonly<{ slug: string; onSaved: () => void; defaultDate?: string }>) {
  const create = useCreateOneOff(slug);
  const sectionTypes = useSectionTypes();
  const [title, setTitle] = useState("");
  const [scheduledAt, setScheduledAt] = useState<ZonedParts>(() =>
    seededWhen(defaultDate),
  );
  const [durationMinutes, setDurationMinutes] = useState("");
  const [place, setPlace] = useState(() => draftFromPlace(EMPTY_PLACE));
  const [structure, setStructure] = useState<SectionTemplateInput[]>([]);

  const disabled =
    title.trim() === "" ||
    scheduledAt.date.trim() === "" ||
    scheduledAt.time.trim() === "";

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (disabled) return;
    const duration =
      durationMinutes.trim() === "" ? undefined : Number(durationMinutes);
    // A create, not a replacement: a place nobody typed is a key left off rather than an explicit
    // empty one, so the session is recorded as having never had a place instead of a cleared one.
    const typed = placeFromDraft(place);
    create.mutate(
      {
        title,
        scheduledAt: { ...scheduledAt, zone: viewerZone() },
        ...(duration !== undefined ? { durationMinutes: duration } : {}),
        ...(placeIsEmpty(typed) ? {} : { place: typed }),
        ...(structure.length > 0 ? { sections: structure } : {}),
      },
      { onSuccess: onSaved },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextInput label="Title" value={title} onChange={setTitle} isRequired />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <DateTimePicker
          label="Starts at"
          value={scheduledAt}
          onChange={setScheduledAt}
          timeZone={viewerZone()}
          isTimeRequired
        />
        <TextInput
          label="Duration (min)"
          type="number"
          value={durationMinutes}
          onChange={setDurationMinutes}
          placeholder="optional"
        />
      </div>

      <MemberZoneSpread
        slug={slug}
        when={scheduledAt}
        zone={viewerZone()}
        durationMinutes={
          durationMinutes.trim() === "" ? null : Number(durationMinutes)
        }
      />

      {/* Shut for the same reason as the series form's: both are optional, both are long, and a
          form that shows every field at once makes the two look as required as the title. */}
      <DisclosureSection
        title="Where it meets"
        summary={placeSummary(place)}
        defaultExpanded={!placeIsBlank(place)}
      >
        {/* No description: a one-off belongs to no series, so there is no inheritance rule to
            state and nothing the three labels do not already say. */}
        <MeetingPlaceFields value={place} onChange={setPlace} />
      </DisclosureSection>

      <StructureSection
        title="Structure"
        value={structure}
        onChange={setStructure}
        sectionTypes={sectionTypes}
        budgetMinutes={
          durationMinutes.trim() === "" ? null : Number(durationMinutes)
        }
      />

      <FormError error={create.isError} />
      <DialogFooter pinned>
        <Button type="submit" isDisabled={disabled}>
          Create
        </Button>
      </DialogFooter>
    </form>
  );
}
