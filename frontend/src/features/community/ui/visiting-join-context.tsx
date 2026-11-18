import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

type Handler = () => void;

type VisitingJoin = {
  handler: Handler | undefined;
  welcoming: boolean;
  register: (handler: Handler) => () => void;
  setWelcoming: (welcoming: boolean) => void;
};

const VisitingJoinContext = createContext<VisitingJoin>({
  handler: undefined,
  welcoming: false,
  register: () => () => {},
  setWelcoming: () => {},
});

/** Lets the page on screen take over what happens after the Visiting bar's Join succeeds. */
export function VisitingJoinProvider({
  children,
}: Readonly<{ children: ReactNode }>) {
  const [handler, setHandler] = useState<Handler | undefined>(undefined);
  const [welcoming, setWelcoming] = useState(false);
  const register = useCallback((next: Handler) => {
    setHandler(() => next);
    return () =>
      setHandler((current) => (current === next ? undefined : current));
  }, []);
  const value = useMemo(
    () => ({ handler, welcoming, register, setWelcoming }),
    [handler, welcoming, register],
  );
  return (
    <VisitingJoinContext.Provider value={value}>
      {children}
    </VisitingJoinContext.Provider>
  );
}

/**
 * Registers `onJoined` for as long as the caller is mounted; the latest one always runs.
 * `welcoming` tells the bar a welcome is playing, so it stays put until the welcome ends.
 */
export function useRegisterVisitingJoin(
  onJoined: Handler,
  welcoming = false,
): void {
  const { register, setWelcoming } = useContext(VisitingJoinContext);
  const latest = useRef(onJoined);
  useEffect(() => {
    latest.current = onJoined;
  });
  useEffect(() => register(() => latest.current()), [register]);
  useEffect(() => {
    setWelcoming(welcoming);
    return () => setWelcoming(false);
  }, [welcoming, setWelcoming]);
}

export function useVisitingJoin(): Handler | undefined {
  return useContext(VisitingJoinContext).handler;
}

export function useVisitingWelcome(): boolean {
  return useContext(VisitingJoinContext).welcoming;
}
