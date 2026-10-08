import { PageHeader } from "@/components/patterns/PageHeader";
import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { tenantWord } from "@/components/patterns/settings-nav";
import { Avatar } from "@/components/ui/Avatar";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { orgHue } from "@/features/admin/ui/OrganizationSwitcher";
import { failedToLoad } from "@/lib/failed-to-load";
import { kindHue } from "@/lib/kind-hue";
import { plural } from "@/lib/plural";

interface HeroProps {
  name: string;
  hue: string;
  badgeUrl: string | null;
  memberCount: number | undefined;
}

function Hero({ name, hue, badgeUrl, memberCount }: Readonly<HeroProps>) {
  return (
    <div className="border-border bg-surface flex items-center gap-4 rounded-md border p-5">
      <Avatar
        name={name}
        hue={hue}
        src={badgeUrl}
        className="size-14 text-base"
      />
      <div className="flex min-w-0 flex-col gap-0.5">
        <p className="font-display text-foreground truncate text-lg font-bold">
          {name}
        </p>
        {memberCount !== undefined && (
          <p className="text-muted-foreground truncate text-sm">
            {plural(memberCount, "member")}
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * The tenant section (path `general`): who the caller is working in and, where the edition
 * supplies an organization picker, every other tenant to switch to. Reads the same data the
 * rail's chips read (`useCurrentOrganization` for kind, `useMyOrganizations` for the badge and
 * accent).
 *
 * Ungated (the catalogue's `general` section carries no `permission`): where the edition supplies
 * switching it lives here, so a plain member without any organization permission must still be
 * able to reach it.
 */
export function TenantSettingsPage() {
  const { OrganizationPicker } = useEdition();
  const { data: organization } = useCurrentOrganization();
  const memberships = useMyOrganizations();
  const { data: organizations } = memberships;

  if (organization === undefined) return null;
  const { label } = tenantWord(organization.kind);
  if (failedToLoad(memberships)) {
    return (
      <PageLoadFailed
        title={label}
        message={`Could not load this ${label.toLowerCase()}.`}
        query={memberships}
        className="gap-8"
      />
    );
  }
  if (organizations === undefined) return null;

  const current = organizations.find((org) => org.id === organization.id);
  const hue =
    current !== undefined ? orgHue(current) : kindHue(organization.name);

  return (
    <div className="flex flex-col gap-8">
      <PageHeader title={label} />
      <Hero
        name={organization.name}
        hue={hue}
        badgeUrl={current?.badgeUrl ?? null}
        memberCount={organization.memberCount}
      />
      {OrganizationPicker !== null && (
        <OrganizationPicker returnTo="/" layout="groups" />
      )}
    </div>
  );
}
