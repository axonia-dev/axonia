import type { ReactNode } from "react";

import { AuthScopeRecovery } from "@/auth/AuthScopeRecovery";
import { AuthProvider } from "@/auth/session";

function isSessionless(
  pathname: string,
  sessionlessPaths: ReadonlyArray<string>,
): boolean {
  const trimmed = pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
  return sessionlessPaths.includes(trimmed);
}

/**
 * The app's session, held by the edge rather than by this process.
 *
 * There is no configuration here any more, and that absence is the change: the authority, the
 * client id, the PKCE settings and the storage that used to be listed on this component have all
 * moved to edge/src/auth.ts, because the browser is no longer the OAuth client. What is left is
 * the provider that asks the edge whether the sealed cookie still names a signed-in member.
 *
 * `sessionlessPaths` are the edition's pages only ever opened without a session, where asking the
 * edge could only be refused. Read once, above the router, so a page listed there must not link
 * into the app.
 */
export function AppAuthProvider({
  sessionlessPaths,
  children,
}: Readonly<{ sessionlessPaths: ReadonlyArray<string>; children: ReactNode }>) {
  return (
    <AuthProvider
      probe={!isSessionless(window.location.pathname, sessionlessPaths)}
    >
      <AuthScopeRecovery />
      {children}
    </AuthProvider>
  );
}
