import { type ReactNode, useId } from "react";

import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { TextInput } from "@/components/ui/TextInput";
import {
  MAIL_SECURITIES,
  type MailSecurity,
  type MailServerView,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import { securityLabel } from "@/selfhosted/features/mail-server/domain/mail-server-copy";
import {
  canKeepStored,
  type DraftProblems,
  type MailServerDraft,
} from "@/selfhosted/features/mail-server/domain/mail-server-form";
import { MailServerPasswordField } from "@/selfhosted/features/mail-server/ui/MailServerPasswordField";
import { ReadOnlyField } from "@/selfhosted/features/mail-server/ui/ReadOnlyField";

const SECURITY_OPTIONS = MAIL_SECURITIES.map((id) => ({
  id,
  label: securityLabel(id),
}));

type Change = (patch: Partial<MailServerDraft>) => void;

type SettingFieldProps = Readonly<{
  label: string;
  setting: Readonly<{ value: string | number | null; lockedBy: string | null }>;
  value: string;
  error: string | undefined;
  maxLength: number;
  type?: "text" | "email" | "number";
  onChange: (value: string) => void;
}>;

function SettingField({
  label,
  setting,
  value,
  error,
  maxLength,
  type = "text",
  onChange,
}: SettingFieldProps) {
  if (setting.lockedBy !== null) {
    return (
      <ReadOnlyField
        label={label}
        value={String(setting.value ?? "")}
        lockedBy={setting.lockedBy}
        {...(error === undefined ? {} : { error })}
      />
    );
  }
  return (
    <TextInput
      label={label}
      type={type}
      value={value}
      onChange={onChange}
      maxLength={maxLength}
      {...(error === undefined ? {} : { errorMessage: error })}
    />
  );
}

type SecurityProps = Readonly<{
  setting: MailServerView["security"];
  value: MailSecurity;
  onChange: Change;
}>;

function SecurityField({ setting, value, onChange }: SecurityProps) {
  if (setting.lockedBy !== null) {
    return (
      <ReadOnlyField
        label="Security"
        value={securityLabel(setting.value ?? value)}
        lockedBy={setting.lockedBy}
      />
    );
  }
  return (
    <SegmentedControl
      label="Security"
      value={value}
      options={SECURITY_OPTIONS}
      onChange={(id) => {
        const security = MAIL_SECURITIES.find((option) => option === id);
        if (security !== undefined) onChange({ security });
      }}
    />
  );
}

type Props = Readonly<{
  view: MailServerView;
  draft: MailServerDraft;
  problems: DraftProblems;
  onChange: Change;
}>;

function Group({
  title,
  children,
}: Readonly<{ title: string; children: ReactNode }>) {
  const titleId = useId();
  return (
    <div
      role="group"
      aria-labelledby={titleId}
      className="flex min-w-0 flex-col gap-3"
    >
      <h3 id={titleId} className="text-foreground text-sm font-semibold">
        {title}
      </h3>
      {children}
    </div>
  );
}

/** SMTP server, then Sender: each field typed, read only when the deployment sets it. */
export function MailServerFields({ view, draft, problems, onChange }: Props) {
  return (
    <>
      <Group title="SMTP server">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_8rem]">
          <SettingField
            label="Host"
            setting={view.host}
            value={draft.host}
            error={problems.host}
            maxLength={253}
            onChange={(host) => onChange({ host })}
          />
          <SettingField
            label="Port"
            type="number"
            setting={view.port}
            value={draft.port}
            error={problems.port}
            maxLength={5}
            onChange={(port) => onChange({ port })}
          />
        </div>
        <SecurityField
          setting={view.security}
          value={draft.security}
          onChange={onChange}
        />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <SettingField
            label="Username"
            setting={view.username}
            value={draft.username}
            error={problems.username}
            maxLength={255}
            onChange={(username) => onChange({ username })}
          />
          <MailServerPasswordField
            password={view.password}
            draft={draft}
            error={problems.password}
            keepable={canKeepStored(draft, view)}
            onChange={onChange}
          />
        </div>
      </Group>
      <Group title="Sender">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <SettingField
            label="From address"
            type="email"
            setting={view.fromAddress}
            value={draft.fromAddress}
            error={problems.fromAddress}
            maxLength={254}
            onChange={(fromAddress) => onChange({ fromAddress })}
          />
          <SettingField
            label="From name"
            setting={view.fromName}
            value={draft.fromName}
            error={problems.fromName}
            maxLength={120}
            onChange={(fromName) => onChange({ fromName })}
          />
        </div>
      </Group>
    </>
  );
}
