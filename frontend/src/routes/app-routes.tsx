import type { ReactNode } from "react";
import {
  createRoutesFromChildren,
  Navigate,
  Route,
  type RouteObject,
  useLocation,
  useParams,
  useRoutes,
} from "react-router-dom";

import { AuthGuard } from "@/auth/AuthGuard";
import { useIsDelegated } from "@/auth/delegated-session";
import { SuspendedOrganizationGate } from "@/auth/SuspendedOrganizationGate";
import { useIsOrgless } from "@/auth/use-is-orgless";
import { GlobalShell } from "@/components/patterns/GlobalShell";
import { NotFoundPage } from "@/components/patterns/NotFoundPage";
import { useRememberAppLocation } from "@/components/patterns/settings-return";
import type { EditionSlots } from "@/edition";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { ConsentPage } from "@/features/agent/ui/ConsentPage";
import { MySavedView } from "@/features/bookmarks/ui/MySavedView";
import { CommunicationSettingsPage } from "@/features/community/ui/CommunicationSettingsPage";
import { CommunityOverview } from "@/features/community/ui/CommunityOverview";
import { CommunityPolicyPage } from "@/features/community/ui/CommunityPolicyPage";
import {
  BacklogSection,
  LibrarySection,
} from "@/features/community/ui/CommunitySections";
import { CommunityShell } from "@/features/community/ui/CommunityShell";
import { LookAndFeelEditor } from "@/features/community/ui/LookAndFeelEditor";
import { MembersPage } from "@/features/community/ui/MembersPage";
import { DocPageView } from "@/features/docs/ui/DocPageView";
import { DocsShell } from "@/features/docs/ui/DocsShell";
import { OccurrencePreviewPage } from "@/features/events/ui/OccurrencePreviewPage";
import { SchedulePage } from "@/features/events/ui/SchedulePage";
import { SessionDetailPage } from "@/features/events/ui/SessionDetailPage";
import { ExpertisePage } from "@/features/expertise/ui/ExpertisePage";
import { MyExpertiseView } from "@/features/expertise/ui/MyExpertiseView";
import { TagCurationView } from "@/features/expertise/ui/TagCurationView";
import { HealthSection } from "@/features/health/ui/HealthSection";
import { LearningPage } from "@/features/learning/ui/LearningPage";
import { MyImpactView } from "@/features/recognition/ui/MyImpactView";
import { failedToLoad } from "@/lib/failed-to-load";
import { CommunitiesPage } from "@/pages/CommunitiesPage";
import { CommunityEditPage } from "@/pages/CommunityEditPage";
import { CommunityNewPage } from "@/pages/CommunityNewPage";
import { OrganizationEntry } from "@/routes/organization-entry";
import { OrganizationLoadFailed } from "@/routes/organization-load-failed";
import { SettingsRoutes } from "@/routes/settings-routes";

function ScheduleRoute() {
  const { slug = "" } = useParams();
  return <SchedulePage slug={slug} />;
}

function SessionDetailRoute() {
  const { slug = "" } = useParams();
  return <SessionDetailPage slug={slug} />;
}

function OccurrencePreviewRoute() {
  const { slug = "", seriesId = "", date = "" } = useParams();
  return (
    <OccurrencePreviewPage
      slug={slug}
      seriesId={Number(seriesId)}
      date={date}
    />
  );
}

function CurationRoute() {
  const { slug = "" } = useParams();
  return <TagCurationView slug={slug} />;
}

function ExpertiseRoute() {
  return <ExpertisePage />;
}

function CommunityNotFound() {
  const { slug = "" } = useParams();
  return (
    <NotFoundPage
      homeTo={`/communities/${slug}`}
      homeLabel="Back to community"
    />
  );
}

function EditionRootRedirect() {
  const { RootRedirect } = useEdition();
  return <RootRedirect />;
}

// GlobalShell is enterprise-only: a hub-kind org never renders the list/org-level shell.
// /me/* deep links are preserved by nesting them under the community. /admin/* and
// /settings/* paths no longer live inside this gate's own child routes - movedRoutes
// redirects the former and the settings subtree sits outside by design (see
// SettingsRoutes' hub variant, matched directly against /communities/:slug rather than
// reached through this gate) - so nesting either of them here would never fire.
function EnterpriseShellGate() {
  if (useIsOrgless()) return <OrganizationEntry fallback="/discover" />;
  return <ScopedEnterpriseShell />;
}

// A community link (what every notification mail carries) opened org-less would read the
// community with a token that names no organization: a 401, a fresh sign-in with the same scope,
// and a loop through Keycloak. Nothing under it may mount before an organization is entered.
function CommunityShellGate() {
  if (useIsOrgless()) return <OrganizationEntry fallback="/discover" />;
  return <CommunityShell />;
}

function ScopedEnterpriseShell() {
  const query = useCurrentOrganization();
  const { data: organization } = query;
  const { pathname } = useLocation();
  if (failedToLoad(query)) return <OrganizationLoadFailed query={query} />;
  if (organization === undefined) return null;
  if (organization.kind === "HUB") {
    const nested = pathname.startsWith("/me/") ? pathname : "";
    return <Navigate to={`/communities/${organization.id}${nested}`} replace />;
  }
  return <GlobalShell />;
}

/**
 * The gate on everything that needs a caller, and the viewport box every shell below sizes
 * against. AuthGuard is skipped for a delegated tab: it short-circuits on the absence of an OIDC
 * user and would send the tab to a sign-in it can never complete. The box is the viewport minus
 * whatever the edition's frame around a delegated tab already took.
 */
function AuthenticatedFrame({ children }: Readonly<{ children: ReactNode }>) {
  const delegated = useIsDelegated();
  return (
    <div className={`flex flex-col ${delegated ? "h-full" : "h-dvh"}`}>
      <div className="min-h-0 flex-1">
        <SuspendedOrganizationGate>
          {delegated ? children : <AuthGuard>{children}</AuthGuard>}
        </SuspendedOrganizationGate>
      </div>
    </div>
  );
}

export function AuthenticatedRoutes({
  routes,
}: Readonly<{ routes: RouteObject[] }>) {
  // Records where the member is whenever they are not on a shell-replacing surface
  // (settings, docs), so the way out of either returns them there. Mounted here rather
  // than in App: a public page is not somewhere to come back to.
  useRememberAppLocation();
  return (
    <AuthenticatedFrame>
      <AuthenticatedRouteTable routes={routes} />
    </AuthenticatedFrame>
  );
}

function AuthenticatedRouteTable({
  routes,
}: Readonly<{ routes: RouteObject[] }>) {
  const edition = useEdition();
  return useRoutes([
    ...createRoutesFromChildren(openRoutes(edition)),
    ...routes,
  ]);
}

function openRoutes(edition: EditionSlots): ReactNode {
  return (
    <>
      <Route path="/" element={<EditionRootRedirect />} />
      {/* Settings sits outside both EnterpriseShellGate and CommunityShell: each renders a
          rail that SettingsShell replaces, so nesting settings inside either would stack a
          second layout under the first. */}
      {SettingsRoutes({ base: "", edition })}
      {/* Docs sits outside every gate, like settings: it is the same authored content for
          every member whatever their organization's kind, and DocsShell owns its own chrome
          rather than rendering inside one. Nested under EnterpriseShellGate it was
          unreachable for a hub session, which is what made the account menu's Documentation
          item look like it did nothing. */}
      <Route path="/docs" element={<DocsShell />}>
        <Route index element={<Navigate to="agent-overview" replace />} />
        <Route path=":slug" element={<DocPageView />} />
      </Route>
      {/* Where an MCP client sends the browser to ask for access: outside every gate, like
          docs, since a member reviewing it has not necessarily landed in an organization
          yet. */}
      <Route path="/oauth/authorize" element={<ConsentPage />} />
      <Route element={<EnterpriseShellGate />}>
        <Route path="/communities" element={<CommunitiesPage />} />
        <Route path="/communities/new" element={<CommunityNewPage />} />
        <Route
          path="/communities/kinds"
          element={<Navigate to="/admin/kinds" replace />}
        />
        <Route
          path="/library/types"
          element={<Navigate to="/admin/library-types" replace />}
        />
        <Route path="/me/impact" element={<MyImpactView />} />
        <Route path="/me/expertise" element={<MyExpertiseView />} />
        <Route path="/me/saved" element={<MySavedView />} />
      </Route>
      {/* The hub variant of the same settings subtree, matched before CommunityShell so a
          hub member's settings replace its rail instead of nesting under it. */}
      {SettingsRoutes({ base: "/communities/:slug", edition })}
      <Route path="/communities/:slug" element={<CommunityShellGate />}>
        <Route index element={<CommunityOverview />} />
        <Route path="backlog" element={<BacklogSection />} />
        <Route path="library" element={<LibrarySection />} />
        <Route path="schedule" element={<ScheduleRoute />} />
        <Route path="sessions/:sessionId" element={<SessionDetailRoute />} />
        <Route
          path="series/:seriesId/occurrences/:date"
          element={<OccurrencePreviewRoute />}
        />
        <Route path="expertise" element={<ExpertiseRoute />} />
        <Route path="expertise/curation" element={<CurationRoute />} />
        <Route path="learning" element={<LearningPage />} />
        <Route path="health" element={<HealthSection />} />
        <Route path="edit" element={<CommunityEditPage />} />
        <Route path="look-and-feel" element={<LookAndFeelEditor />} />
        <Route path="settings/policy" element={<CommunityPolicyPage />} />
        <Route path="settings/members" element={<MembersPage />} />
        <Route
          path="settings/communication"
          element={<CommunicationSettingsPage />}
        />
        <Route path="me/impact" element={<MyImpactView />} />
        <Route path="me/expertise" element={<MyExpertiseView />} />
        <Route path="me/saved" element={<MySavedView />} />
        <Route path="*" element={<CommunityNotFound />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </>
  );
}
