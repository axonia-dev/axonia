import { useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { FormError } from "@/components/patterns/FormError";
import { SettingsCard, SettingsRow } from "@/components/patterns/SettingsCard";
import { Switch } from "@/components/ui/Switch";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMentoringSettings,
  useUpdateMentoringSettings,
} from "@/features/community/api/use-mentoring-settings";
import { communityErrorMessage } from "@/features/community/community-errors";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { OWN_SIGN_IN_REFUSAL } from "@/features/mentoring/mentoring-errors";

function MentoringSwitch({ slug }: Readonly<{ slug: string }>) {
  const settings = useMentoringSettings(slug);
  const update = useUpdateMentoringSettings(slug);

  if (settings.isPending) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (settings.data == null) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        Could not load the mentoring settings.
      </p>
    );
  }
  return (
    <SettingsCard title="Mentoring">
      <SettingsRow>
        <Switch
          label="Mentoring on"
          description="Members can find a mentor or a mentee here. Pairings already started carry on."
          isSelected={settings.data.enabled}
          isDisabled={update.isPending}
          onChange={(enabled) => update.mutate(enabled)}
        />
      </SettingsRow>
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

function body(slug: string, allowed: boolean, delegated: boolean) {
  if (delegated) {
    return (
      <p className="text-muted-foreground text-sm">{OWN_SIGN_IN_REFUSAL}</p>
    );
  }
  if (!allowed) {
    return (
      <p className="text-muted-foreground text-sm">
        No access to mentoring settings.
      </p>
    );
  }
  return <MentoringSwitch slug={slug} />;
}

/** Whether this community is a mentoring guild. Organizers only, like the management policy. */
export function MentoringSettingsPage() {
  const { slug = "" } = useParams();
  const access = useManagementAccess(slug);
  const delegated = useIsDelegated();
  useCommunityCrumbs([{ label: "Mentoring" }]);

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="text-foreground font-display text-xl font-semibold">
          Mentoring
        </h1>
        <p className="text-muted-foreground text-sm">
          Whether members of this community can be paired.
        </p>
      </header>
      <div className="max-w-xl">
        {body(slug, access.managementPolicy, delegated)}
      </div>
    </section>
  );
}
