import { useId, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import {
  SettingsCard,
  SettingsDivider,
  SettingsFooter,
  SettingsRow,
} from "@/components/patterns/SettingsCard";
import { Button } from "@/components/ui/Button";
import { NumberField } from "@/components/ui/NumberField";
import { Switch } from "@/components/ui/Switch";
import { useUpdateCommunicationSettings } from "@/features/community/api/use-communication-settings";
import { communityErrorMessage } from "@/features/community/community-errors";
import type {
  CommunicationSettings,
  CommunicationSettingsInput,
} from "@/features/community/domain/communication-settings";
import {
  communicationFieldError,
  parseCommunicationSettingsInput,
  SESSION_UPCOMING_LEAD_DAYS_MAX,
  WRAP_UP_GRACE_HOURS_MAX,
} from "@/features/community/domain/communication-settings";

function toFormState(
  settings: CommunicationSettings,
): CommunicationSettingsInput {
  return {
    notifySessionUpcoming: settings.notifySessionUpcoming,
    notifySessionWrapUp: settings.notifySessionWrapUp,
    sessionUpcomingLeadDays: String(settings.sessionUpcomingLeadDays),
    wrapUpGraceHours: String(settings.wrapUpGraceHours),
  };
}

/** The organization's kill switch always wins over whatever is set here. */
function OrganizationOffNotice() {
  return (
    <p role="status" className="text-warning px-4 pt-3 text-sm">
      Email is turned off for the whole organization. Nothing here will send
      until an admin turns it back on.
    </p>
  );
}

interface TimingProps {
  children: React.ReactNode;
}

function Timing({ children }: Readonly<TimingProps>) {
  return (
    <div className="text-muted-foreground flex flex-wrap items-center gap-2 px-4 pb-3.5 text-sm">
      {children}
    </div>
  );
}

interface SessionTimingRowProps {
  /** Doubles as the switch's accessible name and the row's own visible label. */
  switchLabel: string;
  isSelected: boolean;
  onSelectedChange: (next: boolean) => void;
  /** The number field's complete accessible name; see `NumberField`'s own
   *  contract - it must read out the unit in words. */
  numberLabel: string;
  unit: string;
  value: string;
  onValueChange: (value: string) => void;
  maxValue: number;
  errorMessage: string | undefined;
  /** The rest of the sentence after the number, e.g. "before the session". */
  suffix: string;
}

/**
 * One switch plus its timing, read as a sentence: "Sent [3] days before the
 * session". The two calls in `SessionMessagesCard` are identical in shape by
 * construction, so this is the one place that shape is written down.
 */
function SessionTimingRow({
  switchLabel,
  isSelected,
  onSelectedChange,
  numberLabel,
  unit,
  value,
  onValueChange,
  maxValue,
  errorMessage,
  suffix,
}: Readonly<SessionTimingRowProps>) {
  const labelId = useId();

  return (
    <>
      <SettingsRow>
        <span
          id={labelId}
          className="text-foreground flex-1 text-sm font-semibold"
        >
          {switchLabel}
        </span>
        <Switch
          aria-labelledby={labelId}
          isSelected={isSelected}
          onChange={onSelectedChange}
        />
      </SettingsRow>
      <Timing>
        Sent
        <NumberField
          label={numberLabel}
          unit={unit}
          value={value}
          onChange={onValueChange}
          minValue={0}
          maxValue={maxValue}
          {...(errorMessage !== undefined ? { errorMessage } : {})}
        />
        {suffix}
      </Timing>
    </>
  );
}

interface SessionMessagesCardProps {
  slug: string;
  initial: CommunicationSettings;
  communicationsEnabled: boolean;
}

export function SessionMessagesCard({
  slug,
  initial,
  communicationsEnabled,
}: Readonly<SessionMessagesCardProps>) {
  const [form, setForm] = useState(toFormState(initial));
  const update = useUpdateCommunicationSettings(slug);
  const parsed = parseCommunicationSettingsInput(form);
  const leadDaysError = communicationFieldError(
    form.sessionUpcomingLeadDays,
    SESSION_UPCOMING_LEAD_DAYS_MAX,
  );
  const graceHoursError = communicationFieldError(
    form.wrapUpGraceHours,
    WRAP_UP_GRACE_HOURS_MAX,
  );

  function save() {
    if (parsed !== null) update.mutate(parsed);
  }

  return (
    <SettingsCard title="Session messages">
      {!communicationsEnabled && <OrganizationOffNotice />}
      <SessionTimingRow
        switchLabel="Session reminders"
        isSelected={form.notifySessionUpcoming}
        onSelectedChange={(next) =>
          setForm({ ...form, notifySessionUpcoming: next })
        }
        numberLabel="Days of notice before a session"
        unit="days"
        value={form.sessionUpcomingLeadDays}
        onValueChange={(value) =>
          setForm({ ...form, sessionUpcomingLeadDays: value })
        }
        maxValue={SESSION_UPCOMING_LEAD_DAYS_MAX}
        errorMessage={leadDaysError}
        suffix="before the session"
      />
      <SettingsDivider />
      <SessionTimingRow
        switchLabel="Session wrap-ups"
        isSelected={form.notifySessionWrapUp}
        onSelectedChange={(next) =>
          setForm({ ...form, notifySessionWrapUp: next })
        }
        numberLabel="Hours before the wrap-up sends"
        unit="hours"
        value={form.wrapUpGraceHours}
        onValueChange={(value) => setForm({ ...form, wrapUpGraceHours: value })}
        maxValue={WRAP_UP_GRACE_HOURS_MAX}
        errorMessage={graceHoursError}
        suffix="after a session is held"
      />
      <SettingsDivider />
      <SettingsFooter>
        <Button onPress={save} isDisabled={parsed === null || update.isPending}>
          Save
        </Button>
      </SettingsFooter>
      {update.isError && (
        <div className="px-4 pb-3">
          <FormError
            error={update.error}
            message={communityErrorMessage(update.error)}
          />
        </div>
      )}
    </SettingsCard>
  );
}
