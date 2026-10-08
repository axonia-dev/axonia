import {
  Group,
  Input,
  Label,
  NumberField as AriaNumberField,
} from "react-aria-components";

interface NumberFieldProps {
  /**
   * The field's complete accessible name - it must read out the unit in
   * words (e.g. "Days of notice before a session" for `unit="days"`,
   * "Hours before the wrap-up sends" for `unit="hours"`). `unit` itself is
   * rendered `aria-hidden` inside the control, so it is never announced;
   * a `label` that omits the unit word leaves the setting's unit unstated
   * to a screen reader.
   */
  label: string;
  unit: string;
  value: string;
  onChange: (value: string) => void;
  minValue: number;
  maxValue: number;
  errorMessage?: string;
}

/**
 * A number with its unit attached, for a setting that reads as a sentence:
 * "Sent [3] days before the session". The unit is what two removed paragraphs
 * of help text were mostly there to say, so it belongs inside the control.
 *
 * The value stays a string all the way through. An empty field is a real state
 * a person passes through while typing, and a `number` that turns it into `0`
 * or `NaN` would either silently change the setting or block the keystroke.
 */
export function NumberField({
  label,
  unit,
  value,
  onChange,
  minValue,
  maxValue,
  errorMessage,
}: Readonly<NumberFieldProps>) {
  return (
    <AriaNumberField
      value={value === "" ? Number.NaN : Number(value)}
      onChange={(next) => onChange(Number.isNaN(next) ? "" : String(next))}
      minValue={minValue}
      maxValue={maxValue}
      // React Aria's default ("snap") silently rewrites an out-of-range commit to the
      // nearest bound - 337 becomes 336 with no explanation. "validate" instead lets a
      // typed or pasted out-of-range value survive to `onChange` uncorrected, so the
      // caller's own bound message (`communicationFieldError`) can state it instead of
      // the control silently fixing it.
      commitBehavior="validate"
      isInvalid={errorMessage !== undefined}
      formatOptions={{ useGrouping: false }}
      className="inline-flex flex-col gap-1"
    >
      <Label className="sr-only">{label}</Label>
      <Group className="border-border-strong bg-surface-sunken focus-within:ring-focus-ring aria-[invalid]:border-danger inline-flex items-center overflow-hidden rounded-md border focus-within:ring-2">
        <Input className="text-foreground w-14 bg-transparent px-2.5 py-1.5 text-center text-sm tabular-nums outline-none" />
        <span
          aria-hidden
          className="border-border-strong text-muted-foreground border-l px-2.5 py-1.5 text-xs"
        >
          {unit}
        </span>
      </Group>
      {errorMessage !== undefined && (
        <span role="alert" className="text-danger text-xs">
          {errorMessage}
        </span>
      )}
    </AriaNumberField>
  );
}
