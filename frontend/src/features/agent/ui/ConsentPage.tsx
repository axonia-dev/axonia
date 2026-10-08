import type { QueryObserverResult } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { PreviewFrame } from "@/components/patterns/PreviewFrame";
import { useActiveOrganizationId } from "@/features/admin/api/use-active-organization-id";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { useMe } from "@/features/identity/api/use-me";
import { ApiError } from "@/lib/api-client";
import {
  failedToLoad,
  type RetryableRead,
  retryOfFailed,
} from "@/lib/failed-to-load";

import {
  useApproveConnection,
  useAuthorizationReview,
  useDenyConnection,
} from "../api/use-agent-connections";
import {
  type AuthorizationOutcome,
  type AuthorizationRequest,
  authorizationRequestFrom,
  claimedClientHost,
  type RefusalReason,
  returnHost,
} from "../domain/authorization-request";
import type { AgentScopeCode } from "../domain/personal-access-token";
import { ConsentCard } from "./ConsentCard";
import { ConsentNotice, ConsentRefusal } from "./ConsentRefusal";
import { leaveFor } from "./leave-for";
import { OrganizationRequired } from "./OrganizationRequired";
import { useConsentOrganization } from "./use-consent-organization";

interface MutationState {
  isPending: boolean;
  isError: boolean;
  error?: unknown;
}

type QueryState<T> = Pick<
  QueryObserverResult<T>,
  "data" | "errorUpdateCount" | "isFetching" | "refetch"
>;

/** The consent card names the person and the organization, so it waits for both rather than
 *  rendering blanks that fill in and push the answer buttons down. */
type Viewer =
  | { state: "loading" }
  | { state: "failed"; retry: RetryableRead }
  | { state: "known"; actsAs: string };

function viewerOf(
  me: QueryState<{ displayName: string }>,
  organizations: QueryState<unknown>,
): Viewer {
  if (failedToLoad(me) || failedToLoad(organizations)) {
    return { state: "failed", retry: retryOfFailed([me, organizations]) };
  }
  if (me.data === undefined || organizations.data === undefined) {
    return { state: "loading" };
  }
  return { state: "known", actsAs: me.data.displayName };
}

function isPendingOf(approve: MutationState, deny: MutationState): boolean {
  return approve.isPending || deny.isPending;
}

function statusOf(error: unknown): number | undefined {
  return error instanceof ApiError ? error.status : undefined;
}

function errorMessageOf(
  approve: MutationState,
  deny: MutationState,
): string | null {
  if (statusOf(approve.error) === 429 || statusOf(deny.error) === 429) {
    return "Too many attempts in a short time. Wait a minute, then try again.";
  }
  if (statusOf(approve.error) === 409) {
    return "The organization changed. Reload to review again.";
  }
  if (approve.isError) return "Could not connect. Try again.";
  if (deny.isError) return "Could not send the answer. Try again.";
  return null;
}

function Loading() {
  return (
    <p role="status" className="text-muted-foreground text-sm">
      Loading…
    </p>
  );
}

/** Inline rather than the full-screen form: PreviewFrame already draws the page around it. */
function RequestLoadFailed({ read }: Readonly<{ read: RetryableRead }>) {
  return (
    <LoadFailed inline message="Could not load this request." query={read} />
  );
}

function Leaving({ host }: Readonly<{ host: string | null }>) {
  return (
    <p role="status" className="text-muted-foreground text-sm">
      {host === null ? "Returning…" : `Returning to ${host}…`}
    </p>
  );
}

interface ConsentOutcomeProps {
  refused: RefusalReason | null;
  leaving: boolean;
  review: ReturnType<typeof useAuthorizationReview>;
  request: AuthorizationRequest;
  viewer: Viewer;
  isPending: boolean;
  error: string | null;
  onAllow: (scopes: AgentScopeCode[]) => void;
  onDeny: () => void;
}

/** What the page shows for the current review, in one place so ConsentBody stays a plain
 *  sequence of hooks and derived values rather than a five-way branch of its own. */
function ConsentOutcome({
  refused,
  leaving,
  review,
  request,
  viewer,
  isPending,
  error,
  onAllow,
  onDeny,
}: Readonly<ConsentOutcomeProps>) {
  if (refused !== null) {
    return <ConsentRefusal reason={refused} />;
  }
  if (leaving) {
    return <Leaving host={returnHost(request)} />;
  }
  if (failedToLoad(review)) {
    return <RequestLoadFailed read={review} />;
  }
  if (review.data === undefined || review.data.outcome === "REDIRECT") {
    return <Loading />;
  }
  if (review.data.outcome === "REFUSED") {
    return <ConsentRefusal reason={review.data.reason} />;
  }
  if (viewer.state === "failed") {
    return <RequestLoadFailed read={viewer.retry} />;
  }
  if (viewer.state === "loading") {
    return <Loading />;
  }
  return (
    <ConsentCard
      clientName={review.data.clientName}
      clientHost={review.data.clientHost}
      requestedScopes={review.data.requestedScopes}
      actsAs={viewer.actsAs}
      returnsTo={returnHost(request)}
      isPending={isPending}
      error={error}
      onAllow={onAllow}
      onDeny={onDeny}
    />
  );
}

function ConsentBody() {
  const [params] = useSearchParams();
  const request = useMemo(() => authorizationRequestFrom(params), [params]);
  const review = useAuthorizationReview(request);
  const approve = useApproveConnection();
  const deny = useDenyConnection();
  const me = useMe();
  const organizations = useMyOrganizations();
  const [refused, setRefused] = useState<RefusalReason | null>(null);
  const [leaving, setLeaving] = useState(false);
  // The organization the card shows, sent with the answer so a switch in another tab cannot
  // bind the connection to an organization the person never saw here.
  const organizationId = useActiveOrganizationId() ?? "";

  const redirect =
    review.data?.outcome === "REDIRECT" ? review.data.redirectUrl : null;
  useEffect(() => {
    if (redirect !== null) leaveFor(redirect);
  }, [redirect]);

  function follow(outcome: AuthorizationOutcome) {
    if (outcome.outcome === "REDIRECT") {
      setLeaving(true);
      leaveFor(outcome.redirectUrl);
    }
    if (outcome.outcome === "REFUSED") setRefused(outcome.reason);
  }

  return (
    <ConsentOutcome
      refused={refused}
      leaving={leaving}
      review={review}
      request={request}
      viewer={viewerOf(me, organizations)}
      isPending={isPendingOf(approve, deny)}
      error={errorMessageOf(approve, deny)}
      onAllow={(scopes) => {
        deny.reset();
        approve.mutate(
          { request, scopes, organizationId },
          { onSuccess: follow },
        );
      }}
      onDeny={() => {
        approve.reset();
        deny.mutate(request, { onSuccess: follow });
      }}
    />
  );
}

function useClaimedClientHost(): string | null {
  const [params] = useSearchParams();
  return claimedClientHost(authorizationRequestFrom(params));
}

/** The review only runs once the token holds the organization the connection binds to. */
function ConsentOrganizationGate() {
  const { state, memberships } = useConsentOrganization();
  const claimedHost = useClaimedClientHost();
  switch (state) {
    case "held":
      return <ConsentBody />;
    case "entering":
      return <Loading />;
    case "failed":
      return <RequestLoadFailed read={memberships} />;
    case "none":
      return (
        <ConsentNotice
          title="No organization"
          line="Agents need an organization."
        />
      );
    case "choose":
      return (
        <OrganizationRequired
          title={
            claimedHost === null
              ? "Connect an agent"
              : `Connect an app from ${claimedHost}`
          }
        />
      );
  }
}

function ConsentGate() {
  const delegated = useIsDelegated();
  if (delegated) {
    return (
      <ConsentNotice
        title="Connection refused"
        line="Needs the person’s own sign-in."
      />
    );
  }
  return <ConsentOrganizationGate />;
}

/** Where an MCP client sends the browser to ask for access. Outside the app shell, like the sign-in page. */
export function ConsentPage() {
  return (
    <PreviewFrame>
      <ConsentGate />
    </PreviewFrame>
  );
}
