// The customer app's session: the shared core in src/auth/core, under this app's own names.

import type { ReactNode } from "react";

import { AuthProvider as SessionProvider } from "@/auth/core/session";

export { type AuthContextProps, useAuth } from "@/auth/core/session";

const NAMES = {
  channel: "axonia.session",
  lock: "axonia.session-renewal",
  signInStarted: "axonia.sign-in-started",
};

/**
 * `probe` false settles signed out without asking the edge: for a page that is only ever opened
 * without a session, where the edge could only refuse the cookie a sign-out left behind.
 */
export function AuthProvider({
  children,
  probe,
}: Readonly<{ children: ReactNode; probe?: boolean }>) {
  return (
    <SessionProvider names={NAMES} probe={probe}>
      {children}
    </SessionProvider>
  );
}
