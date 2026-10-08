import { useState } from "react";

import { PageHeader } from "@/components/patterns/PageHeader";
import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { useEdition } from "@/edition-context";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import { useMe } from "@/features/identity/api/use-me";
import {
  AccountExpertise,
  visibleAreas,
} from "@/features/identity/ui/AccountExpertise";
import { AccountHero } from "@/features/identity/ui/AccountHero";
import { AccountTimezone } from "@/features/identity/ui/AccountTimezone";
import { AvatarUploadDialog } from "@/features/identity/ui/AvatarUploadDialog";
import { failedToLoad } from "@/lib/failed-to-load";

export function AccountProfilePage() {
  const { OrganizationPicker } = useEdition();
  const me = useMe();
  const organizations = useMyOrganizations();
  const selfView = useSelfView();
  const [changingPicture, setChangingPicture] = useState(false);

  if (failedToLoad(me)) {
    return (
      <PageLoadFailed
        title="Account"
        message="Could not load this account."
        query={me}
      />
    );
  }
  if (me.data === undefined) return null;

  const expertiseCount = selfView.data
    ? visibleAreas(selfView.data).length
    : undefined;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Account" />
      <AccountHero
        user={me.data}
        organizationCount={organizations.data?.length}
        expertiseCount={expertiseCount}
        onChangePicture={() => setChangingPicture(true)}
      />
      {OrganizationPicker !== null && (
        <OrganizationPicker returnTo="/" layout="list" />
      )}
      <AccountTimezone />
      <AccountExpertise />
      <AvatarUploadDialog
        isOpen={changingPicture}
        onOpenChange={setChangingPicture}
        hasOverride={me.data.avatarIsOwn}
        currentUrl={me.data.avatarUrl ?? null}
      />
    </div>
  );
}
