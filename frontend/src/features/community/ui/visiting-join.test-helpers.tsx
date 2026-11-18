import type { ReactNode } from "react";

import {
  useVisitingJoin,
  VisitingJoinProvider,
} from "@/features/community/ui/visiting-join-context";

function BarJoin() {
  const onJoined = useVisitingJoin();
  return (
    <button type="button" onClick={() => onJoined?.()}>
      Join
    </button>
  );
}

/** Stands in for the Visiting bar: a Join that runs whatever the page registered. */
export function WithVisitingJoin({
  children,
}: Readonly<{ children: ReactNode }>) {
  return (
    <VisitingJoinProvider>
      <BarJoin />
      {children}
    </VisitingJoinProvider>
  );
}
