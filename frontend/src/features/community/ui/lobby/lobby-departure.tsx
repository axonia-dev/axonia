import { createContext, type ReactNode, useContext } from "react";

import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";

type Depart = ReturnType<typeof useDepartForCeremony>;

const LobbyDepart = createContext<Depart | null>(null);

/**
 * Holds the departure where it outlives any one Join: a threshold dismissed, or a search result
 * that moved, while its join was still running would otherwise drop the Welcome that follows.
 */
export function LobbyDeparture({
  children,
}: Readonly<{ children: ReactNode }>) {
  const depart = useDepartForCeremony();
  return <LobbyDepart value={depart}>{children}</LobbyDepart>;
}

export function useLobbyDepart(): Depart {
  const own = useDepartForCeremony();
  return useContext(LobbyDepart) ?? own;
}
