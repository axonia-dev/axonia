import type { ReactNode } from "react";

interface SettingsCardProps {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}

/**
 * A settings surface: a titled card whose rows are separated by rules. Distinct
 * from `SectionCard`, which is the community home's own surface and carries an
 * accent icon and a link action rather than an arbitrary control.
 */
export function SettingsCard({
  title,
  action,
  children,
}: Readonly<SettingsCardProps>) {
  return (
    <section className="border-border bg-surface rounded-lg border shadow-sm">
      <header className="flex items-center justify-between gap-3 px-4 pt-3.5 pb-3">
        <h2 className="font-display text-foreground text-sm font-semibold">
          {title}
        </h2>
        {action}
      </header>
      <SettingsDivider />
      {children}
    </section>
  );
}

export function SettingsDivider() {
  return <div className="bg-border h-px" aria-hidden />;
}

export function SettingsRow({ children }: Readonly<{ children: ReactNode }>) {
  return <div className="flex items-center gap-3 px-4 py-3">{children}</div>;
}

export function SettingsFooter({
  children,
}: Readonly<{ children: ReactNode }>) {
  return <div className="flex justify-end px-4 py-3">{children}</div>;
}
