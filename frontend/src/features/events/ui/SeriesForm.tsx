import type { SyntheticEvent } from "react";
import { useMemo, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { ComboBox } from "@/components/ui/ComboBox";
import { DatePicker } from "@/components/ui/DatePicker";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { DisclosureSection } from "@/components/ui/DisclosureSection";
import { Select } from "@/components/ui/Select";
import { TextInput } from "@/components/ui/TextInput";
import { TimePicker } from "@/components/ui/TimePicker";
import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useStartSeries,
  useUpdateSeries,
} from "@/features/events/api/use-series";
import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";
import type { MeetingPlaceDraft } from "@/features/events/domain/meeting-place";
import {
  draftFromPlace,
  EMPTY_PLACE,
  placeFromDraft,
} from "@/features/events/domain/meeting-place";
import { describeNextOccurrence } from "@/features/events/domain/occurrence-preview";
import type {
  Cadence,
  CadenceUnit,
  Series,
} from "@/features/events/domain/series";
import { MeetingPlaceFields } from "@/features/events/ui/MeetingPlaceFields";
import { MemberZoneSpread } from "@/features/events/ui/MemberZoneSpread";
import {
  placeIsBlank,
  placeSummary,
} from "@/features/events/ui/series-form-summaries";
import { StructureSection } from "@/features/events/ui/StructureSection";
import { ignoreTypedZoneText, viewerZone, zoneOptions } from "@/lib/zones";

interface Props {
  slug: string;
  onSaved: (series: { id: number }) => void;
  series?: Series;
  /** Pre-fills the start date when creating (e.g. a calendar day right-click). */
  defaultStartsOn?: string;
}

const UNITS: { id: CadenceUnit; label: string }[] = [
  { id: "DAY", label: "Day" },
  { id: "WEEK", label: "Week" },
  { id: "MONTH", label: "Month" },
  { id: "YEAR", label: "Year" },
];

function cadencePreview(count: number, unit: CadenceUnit): string {
  const label = (UNITS.find((u) => u.id === unit)?.label ?? "").toLowerCase();
  if (!Number.isInteger(count) || count < 1 || count > 999) {
    return "Enter a whole number of 1 or more";
  }
  return `Every ${count} ${label}${count === 1 ? "" : "s"}`;
}

interface FormState {
  title: string;
  cadenceCount: string;
  cadenceUnit: CadenceUnit;
  startsOn: string;
  preferredTime: string;
  timeZone: string;
  durationMinutes: string;
  place: MeetingPlaceDraft;
  structure: SectionTemplateInput[];
}

function initialState(series?: Series, defaultStartsOn?: string): FormState {
  if (!series) {
    return {
      title: "",
      cadenceCount: "1",
      cadenceUnit: "WEEK",
      startsOn: defaultStartsOn ?? "",
      preferredTime: "",
      timeZone: viewerZone(),
      durationMinutes: "",
      place: draftFromPlace(EMPTY_PLACE),
      structure: [],
    };
  }
  return {
    title: series.title,
    cadenceCount: String(series.cadence.count),
    cadenceUnit: series.cadence.unit,
    startsOn: series.startsOn,
    preferredTime: series.preferredTime ?? "",
    timeZone: series.timeZone,
    durationMinutes:
      series.durationMinutes === null ? "" : String(series.durationMinutes),
    place: draftFromPlace(series.place),
    structure: series.defaultStructure,
  };
}

export function SeriesForm({
  slug,
  onSaved,
  series,
  defaultStartsOn,
}: Readonly<Props>) {
  const editing = series !== undefined;
  const sectionTypes = useSectionTypes();
  const start = useStartSeries(slug);
  const update = useUpdateSeries(slug);

  const init = initialState(series, defaultStartsOn);
  const [title, setTitle] = useState(init.title);
  const [cadenceCount, setCadenceCount] = useState(init.cadenceCount);
  const [cadenceUnit, setCadenceUnit] = useState<CadenceUnit>(init.cadenceUnit);
  const [startsOn, setStartsOn] = useState(init.startsOn);
  const [preferredTime, setPreferredTime] = useState(init.preferredTime);
  const [timeZone, setTimeZone] = useState(init.timeZone);
  const zones = useMemo(() => zoneOptions(timeZone), [timeZone]);
  const [durationMinutes, setDurationMinutes] = useState(init.durationMinutes);
  const [place, setPlace] = useState(init.place);
  const [structure, setStructure] = useState<SectionTemplateInput[]>(
    init.structure,
  );

  const nextOccurrence = describeNextOccurrence({
    startsOn,
    preferredTime,
    timeZone,
    viewerZone: viewerZone(),
  });

  const budget = durationMinutes.trim() === "" ? null : Number(durationMinutes);
  const cadenceCountNum = Number(cadenceCount);
  const cadenceValid =
    Number.isInteger(cadenceCountNum) &&
    cadenceCountNum >= 1 &&
    cadenceCountNum <= 999;
  const disabled =
    title.trim() === "" || startsOn.trim() === "" || !cadenceValid;
  const isError = editing ? update.isError : start.isError;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    const input = {
      title,
      cadence: { count: cadenceCountNum, unit: cadenceUnit } satisfies Cadence,
      startsOn,
      ...(preferredTime.trim() !== "" ? { preferredTime } : {}),
      timeZone,
      ...(budget !== null ? { durationMinutes: budget } : {}),
      // Always sent, never spread away: the update is a full replacement, so an omitted place is
      // a cleared place. All three blank is the deliberate "this series has no standing place".
      place: placeFromDraft(place),
      defaultStructure: structure,
    };
    if (editing) {
      update.mutate({ id: series.id, input }, { onSuccess: (s) => onSaved(s) });
    } else {
      start.mutate(input, { onSuccess: (s) => onSaved(s) });
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextInput label="Title" value={title} onChange={setTitle} isRequired />
      {/* The four scheduling answers as two rows of two. Each is a short control, and stacked
          down a panel this wide they left most of every row empty. */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <div className="flex items-end gap-3">
            <TextInput
              label="Repeats every"
              type="number"
              value={cadenceCount}
              onChange={setCadenceCount}
              className="w-24"
            />
            <Select
              label="Unit"
              hideLabel
              value={cadenceUnit}
              options={UNITS}
              onChange={setCadenceUnit}
              className="flex-1"
            />
          </div>
          <p className="text-muted-foreground text-sm">
            {cadencePreview(cadenceCountNum, cadenceUnit)}
          </p>
        </div>
        <DatePicker
          label="Starts on"
          value={startsOn}
          onChange={setStartsOn}
          isRequired
        />
        <div className="flex flex-col gap-1">
          <div className="flex items-end gap-3">
            <TimePicker
              label="Preferred time"
              value={preferredTime}
              onChange={setPreferredTime}
              className="w-24"
            />
            <div className="flex-1">
              <ComboBox
                label="Time zone"
                hideLabel
                value={timeZone}
                options={zones}
                onInputChange={ignoreTypedZoneText}
                onSelect={(option) => setTimeZone(option.id)}
              />
            </div>
          </div>
          {nextOccurrence !== null && (
            <p className="text-muted-foreground text-sm">{nextOccurrence}</p>
          )}
        </div>
        <TextInput
          label="Session length (min)"
          type="number"
          value={durationMinutes}
          onChange={setDurationMinutes}
          placeholder="optional"
        />
      </div>

      {/* `ZonedParts.time` is HH:MM; a stored preferred time arrives carrying seconds. */}
      <MemberZoneSpread
        slug={slug}
        when={{ date: startsOn, time: preferredTime.slice(0, 5) }}
        zone={timeZone}
        durationMinutes={budget}
      />

      {/* Both optional, both long, and both opened already holding something when a series that
          has one is edited: shut on an answer the organizer put there would hide it. */}
      <DisclosureSection
        title="Where it meets"
        summary={placeSummary(place)}
        defaultExpanded={!placeIsBlank(place)}
      >
        <MeetingPlaceFields value={place} onChange={setPlace} />
      </DisclosureSection>

      <StructureSection
        title="Default structure"
        value={structure}
        onChange={setStructure}
        sectionTypes={sectionTypes}
        budgetMinutes={budget}
      />

      <FormError error={isError} />
      <DialogFooter pinned>
        <Button type="submit" isDisabled={disabled}>
          {editing ? "Save" : "Start"}
        </Button>
      </DialogFooter>
    </form>
  );
}
