import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { Slider } from "@/components/ui/Slider";
import type { Section } from "@/features/events/domain/section";
import { eventsErrorMessage } from "@/features/events/events-errors";
import {
  budgetHint,
  MIN_MINUTES,
  MINUTE_STEP,
  sliderBounds,
} from "@/features/events/ui/slot-dialog-helpers";

interface Props {
  minutes: number;
  onMinutesChange: (value: number) => void;
  section: Section;
  /** Lets Edit mode discount the slot's own current time from the section's used minutes. */
  excludeMinutes?: number;
  onCancel: () => void;
  onSubmit: () => void;
  submitLabel: string;
  submitDisabled: boolean;
  error: unknown;
}

/**
 * The tail shared by every slot form - AddSlotForm, EditSlotForm, and RecordSlotForm: the
 * minutes-vs-section-budget slider, the Cancel/submit footer, and the mutation's error message.
 * Each form only differs in what comes before this (source picker, title, description,
 * presenter, tags), so this is what stayed identical across three copies.
 */
export function SlotFormTail({
  minutes,
  onMinutesChange,
  section,
  excludeMinutes = 0,
  onCancel,
  onSubmit,
  submitLabel,
  submitDisabled,
  error,
}: Readonly<Props>) {
  const { remaining, maxMinutes } = sliderBounds(section, excludeMinutes);
  return (
    <>
      <Slider
        label="Minutes"
        value={minutes}
        onChange={onMinutesChange}
        minValue={MIN_MINUTES}
        maxValue={maxMinutes}
        step={MINUTE_STEP}
        formatValue={(value) => `${value} min`}
        {...(remaining !== null ? { markerValue: remaining } : {})}
        {...budgetHint(remaining, minutes)}
      />
      <DialogFooter>
        <Button variant="ghost" size="sm" onPress={onCancel}>
          Cancel
        </Button>
        <Button size="sm" isDisabled={submitDisabled} onPress={onSubmit}>
          {submitLabel}
        </Button>
      </DialogFooter>
      {error != null && (
        <p role="alert" className="text-danger text-sm">
          {eventsErrorMessage(error)}
        </p>
      )}
    </>
  );
}
