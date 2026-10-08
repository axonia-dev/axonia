import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { RouterProvider } from "react-aria-components";
import {
  BrowserRouter,
  useHref,
  useNavigate,
  useRoutes,
} from "react-router-dom";

import { AppAuthProvider } from "@/auth/AppAuthProvider";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { ToastViewport } from "@/components/ui/toast";
import type { Edition } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { UnsubscribePage } from "@/features/communication/ui/UnsubscribePage";
import { queryClient } from "@/lib/query-client";
import { AuthenticatedRoutes } from "@/routes/app-routes";
import { TelemetryView } from "@/telemetry/TelemetryView";
import { ThemeProvider } from "@/theme/ThemeProvider";

export default function App({ edition }: Readonly<{ edition: Edition }>) {
  const { AppFrame } = edition;
  return (
    <EditionProvider edition={edition}>
      <AppAuthProvider sessionlessPaths={edition.sessionlessPaths}>
        <QueryClientProvider client={queryClient}>
          <ThemeProvider>
            <SidebarProvider>
              <ToastViewport />
              <BrowserRouter>
                <TelemetryView />
                <AriaRouterProvider>
                  {/* Above the whole table, public routes included: what the edition frames the
                      app in belongs to the tab, not to a subtree. */}
                  <AppFrame>
                    <EditionRoutes edition={edition} />
                  </AppFrame>
                </AriaRouterProvider>
              </BrowserRouter>
            </SidebarProvider>
          </ThemeProvider>
        </QueryClientProvider>
      </AppAuthProvider>
    </EditionProvider>
  );
}

function EditionRoutes({ edition }: Readonly<{ edition: Edition }>) {
  return useRoutes([
    ...edition.publicRoutes,
    // Public in every edition: the mail-footer unsubscribe link. Holding the token is what
    // authorizes it, never a session, so it sits outside AuthGuard entirely.
    { path: "/unsubscribe", element: <UnsubscribePage /> },
    // Everything else requires a session: the identity provider's, or a delegated one.
    {
      path: "*",
      element: <AuthenticatedRoutes routes={edition.authenticatedRoutes} />,
    },
  ]);
}

// react-aria's own links (react-aria-components' `href` items, e.g. AccountMenu's
// Account settings / Organization settings / Documentation) default to a native `isNative: true`
// router - a real anchor click, full document navigation - unless a `RouterProvider` is in scope.
// This is the one place that needs to exist: every current and future react-aria link in the app
// gets client-side navigation from it, rather than each caller having to know to opt in.
// Trap: `useHref` resolves an absolute or `mailto:` href as a path relative to the current
// location, not as-is - fine for every in-app route today, but a react-aria link ever given
// such an href would be silently mangled. No react-aria element in `src` carries one today;
// every external and `mailto:` link is a plain `<a>`, outside this provider's reach.
function AriaRouterProvider({ children }: Readonly<{ children: ReactNode }>) {
  const navigate = useNavigate();
  return (
    <RouterProvider
      navigate={(path, options) => void navigate(path, options)}
      useHref={useHref}
    >
      {children}
    </RouterProvider>
  );
}
