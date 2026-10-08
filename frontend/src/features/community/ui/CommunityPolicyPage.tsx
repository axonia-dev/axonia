import { useRef, useState } from "react";
import { useParams } from "react-router-dom";

import { notifyError } from "@/components/ui/toast";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useContributionPolicyQuery,
  useUpdateContributionPolicy,
} from "@/features/community/api/use-contribution-policy";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useManagementPolicyQuery,
  useUpdateManagementPolicy,
} from "@/features/community/api/use-management-policy";
import { communityErrorMessage } from "@/features/community/community-errors";
import type { ContributionPolicy } from "@/features/community/domain/contribution-policy";
import type { ManagementPolicy } from "@/features/community/domain/management-policy";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import {
  CADENCE_AXES,
  COMMUNICATION_AXES,
  countOpen,
  DANGER_AXES,
  IDENTITY_AXES,
  type ManagementAxes,
  peopleAxes,
} from "@/features/community/ui/policy-axes";
import { PolicyToggleGroup } from "@/features/community/ui/PolicyToggleGroup";
import { cn } from "@/lib/cn";

interface EditorProps {
  slug: string;
  canContribute: boolean;
  canManage: boolean;
  isHub: boolean;
  initialContribution: ContributionPolicy;
  initialManagement: ManagementPolicy;
}

function PolicyEditor({
  slug,
  canContribute,
  canManage,
  isHub,
  initialContribution,
  initialManagement,
}: Readonly<EditorProps>) {
  const [contribution, setContribution] = useState(initialContribution);
  const [management, setManagement] = useState(initialManagement);
  const updateContribution = useUpdateContributionPolicy(slug);
  const updateManagement = useUpdateManagementPolicy(slug);

  // The last state the server accepted. A failed save snaps the switch back to
  // this, so a toggle never lingers on screen showing a position that isn't saved.
  const savedContribution = useRef(initialContribution);
  const savedManagement = useRef(initialManagement);

  // Both policies are axes on the same community row — a version-locked aggregate —
  // so their writes must never overlap: a concurrent pair races the optimistic-lock
  // guard and one loses. We chain every auto-save through this single promise so
  // they run strictly one after another, however fast switches are flipped.
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const enqueue = (task: () => Promise<void>) => {
    queue.current = queue.current.then(task, task);
  };

  const toggleContribution = (key: keyof ContributionPolicy, next: boolean) => {
    const updated = { ...contribution, [key]: next };
    setContribution(updated);
    enqueue(async () => {
      try {
        await updateContribution.mutateAsync(updated);
        savedContribution.current = updated;
      } catch (cause) {
        setContribution(savedContribution.current);
        notifyError(communityErrorMessage(cause));
      }
    });
  };

  const toggleManagement = (key: keyof ManagementPolicy, next: boolean) => {
    const updated = { ...management, [key]: next };
    setManagement(updated);
    enqueue(async () => {
      try {
        await updateManagement.mutateAsync(updated);
        savedManagement.current = updated;
      } catch (cause) {
        setManagement(savedManagement.current);
        notifyError(communityErrorMessage(cause));
      }
    });
  };

  const people = peopleAxes(isHub);
  const managementAxes: ManagementAxes = [
    ...IDENTITY_AXES,
    ...people,
    ...COMMUNICATION_AXES,
    ...DANGER_AXES,
  ];
  const { openCount, axisCount } = countOpen({
    canContribute,
    contribution,
    canManage,
    management,
    managementAxes,
  });

  return (
    <div className="flex flex-col gap-6">
      <p className="text-muted-foreground flex items-center gap-2 text-sm">
        <span
          aria-hidden="true"
          className={cn(
            "bg-primary size-1.5 rounded-full transition-opacity",
            openCount > 0 ? "opacity-100" : "opacity-0",
          )}
        />
        <span>
          <b className="text-primary-strong tabular-nums">{openCount}</b> of{" "}
          {axisCount} sections opened to members
        </span>
      </p>
      {canContribute && (
        <PolicyToggleGroup
          title="Cadence & events"
          axes={CADENCE_AXES}
          value={contribution}
          onToggle={toggleContribution}
        />
      )}
      {canManage && (
        <>
          <PolicyToggleGroup
            title="Identity & looks"
            axes={IDENTITY_AXES}
            value={management}
            onToggle={toggleManagement}
          />
          <PolicyToggleGroup
            title="People & knowledge"
            axes={people}
            value={management}
            onToggle={toggleManagement}
          />
          <PolicyToggleGroup
            title="Communication"
            axes={COMMUNICATION_AXES}
            value={management}
            onToggle={toggleManagement}
          />
          <PolicyToggleGroup
            title="Danger zone"
            danger
            axes={DANGER_AXES}
            value={management}
            onToggle={toggleManagement}
          />
        </>
      )}
    </div>
  );
}

interface BodyProps {
  slug: string;
  canContribute: boolean;
  canManage: boolean;
  isHub: boolean;
  contribution: ContributionPolicy | undefined;
  management: ManagementPolicy | undefined;
  isPending: boolean;
}

function PolicyBody({
  slug,
  canContribute,
  canManage,
  isHub,
  contribution,
  management,
  isPending,
}: Readonly<BodyProps>) {
  if (!canContribute && !canManage) {
    return <p className="text-muted-foreground text-sm">Organizers only.</p>;
  }
  if (isPending) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (contribution == null || management == null) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        Could not load the community policy.
      </p>
    );
  }
  return (
    <PolicyEditor
      slug={slug}
      canContribute={canContribute}
      canManage={canManage}
      isHub={isHub}
      initialContribution={contribution}
      initialManagement={management}
    />
  );
}

function useIsHubOrganization(): boolean {
  const { data: organization } = useCurrentOrganization();
  return organization?.kind === "HUB";
}

/**
 * The unified governance control room. One page, grouped by the part of the
 * community each switch governs: Cadence & events (the contribution policy, which
 * an organizer may delegate to members), then the organizer-only Identity, People
 * and Danger-zone groups (the management policy). A delegated member sees only the
 * sections they may edit. Every flip saves itself the moment it happens — there is
 * no Save button; a failed write snaps the switch back and raises a toast.
 */
export function CommunityPolicyPage() {
  const { slug = "" } = useParams();
  const access = useManagementAccess(slug);
  const contributionQuery = useContributionPolicyQuery(slug);
  const managementQuery = useManagementPolicyQuery(slug);
  const isHub = useIsHubOrganization();
  useCommunityCrumbs([{ label: "Community policy" }]);

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="text-foreground font-display text-xl font-semibold">
          Community policy
        </h1>
        <p className="text-muted-foreground text-sm">
          Choose what members may do. Each switch opens that section to every
          member.
        </p>
      </header>
      <PolicyBody
        slug={slug}
        canContribute={access.contributionPolicy}
        canManage={access.managementPolicy}
        isHub={isHub}
        contribution={contributionQuery.data}
        management={managementQuery.data}
        isPending={contributionQuery.isPending || managementQuery.isPending}
      />
    </section>
  );
}
