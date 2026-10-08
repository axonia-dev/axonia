import { ListRow } from "@/components/patterns/ListRow";
import { PageHeader } from "@/components/patterns/PageHeader";
import type { SettingsGroup } from "@/components/patterns/settings-nav";

interface SettingsOverviewProps {
  title: string;
  /** Absolute prefix the sections' relative paths hang off. */
  base: string;
  groups: ReadonlyArray<SettingsGroup>;
}

/**
 * The landing page of a settings surface, and the only settings navigation a phone gets:
 * below `md` there is no rail, so this list is how a member moves between sections. It
 * renders from the same catalogue the rail does, which is what stops the two lists drifting.
 */
export function SettingsOverview({
  title,
  base,
  groups,
}: Readonly<SettingsOverviewProps>) {
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title={title} />
      {groups.map((group) => (
        <section key={group.label} className="flex flex-col gap-3">
          {groups.length > 1 && (group.chrome ?? "header") === "header" && (
            <h2 className="text-muted-foreground text-xs font-semibold tracking-[0.14em] uppercase opacity-70">
              {group.label}
            </h2>
          )}
          {groups.length > 1 && (group.chrome ?? "header") === "divider" && (
            <div
              data-testid="settings-group-divider"
              aria-hidden
              className="bg-border h-px"
            />
          )}
          <div className="flex flex-col gap-2">
            {group.items.map((item) => (
              <ListRow
                key={item.path}
                href={`${base}/${item.path}`}
                title={item.label}
                leading={
                  <item.icon
                    size={18}
                    aria-hidden
                    className="text-muted-foreground shrink-0"
                  />
                }
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
