import type { ReactElement } from "react";
import { Link, type LinkProps } from "react-router-dom";

import { MorphLink } from "@/components/patterns/morph/MorphLink";
import { useReadySession } from "@/features/events/api/use-ready-session";

interface ExtraReadiness {
  ready: () => Promise<unknown>;
  prefetch: () => void;
}

type Props = LinkProps & {
  to: string;
  alsoReady?: ExtraReadiness | undefined;
};

function ReadiedSessionLink({
  sessionId,
  alsoReady,
  ...props
}: Readonly<Props & { sessionId: number }>): ReactElement {
  const session = useReadySession();
  return (
    <MorphLink
      {...props}
      ready={() => Promise.all([session.ready(sessionId), alsoReady?.ready()])}
      onIntent={() => {
        session.prefetch(sessionId);
        alsoReady?.prefetch();
      }}
    />
  );
}

/** A link into a session page that morphs once the session is readied; a plain link without one. */
export function SessionMorphLink({
  sessionId,
  alsoReady,
  ...props
}: Readonly<Props & { sessionId: number | null }>): ReactElement {
  if (sessionId === null) return <Link {...props} />;
  return (
    <ReadiedSessionLink
      sessionId={sessionId}
      alsoReady={alsoReady}
      {...props}
    />
  );
}
