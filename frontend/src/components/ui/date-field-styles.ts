// Shared Tailwind class strings for the date/time field primitives, so the
// DatePicker and TimePicker stay visually identical to each other (and to the
// TextInput/Select primitives) without duplicating the long token strings.

export const dateFieldLabelClass = "text-foreground text-sm font-medium";

export const dateFieldGroupClass =
  "flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground transition-colors focus-within:ring-2 focus-within:ring-focus-ring data-[invalid]:border-danger";

export const dateSegmentClass =
  "rounded-sm px-0.5 tabular-nums outline-none data-[placeholder]:text-muted-foreground data-[focused]:bg-primary data-[focused]:text-primary-foreground";
