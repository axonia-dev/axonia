import { createContext, type PropsWithChildren, useContext } from "react";

const DelegatedSessionContext = createContext(false);

/**
 * Marks every screen below it as acting in a delegated session: one another party opened on the
 * person's behalf, whose credential is not the person's own sign-in. Only the edition frame that
 * holds such a session renders it.
 */
export function DelegatedSessionProvider({
  children,
}: Readonly<PropsWithChildren>) {
  return (
    <DelegatedSessionContext value={true}>{children}</DelegatedSessionContext>
  );
}

/**
 * Whether this tab acts in a delegated session, which has no sign-in of its own to leave or renew,
 * and which the backend refuses whatever needs the person's own sign-in.
 *
 * It reads the context rather than storage on purpose. The frame samples its stored session once
 * per mount, so a session that expires mid-render keeps every consumer on the delegated path until
 * the backend's 401 retires the tab. A consumer re-reading storage would instead see the session
 * vanish and quietly hand the tab back to a sign-in it cannot complete.
 */
export function useIsDelegated(): boolean {
  return useContext(DelegatedSessionContext);
}
