import {
  type RefObject,
  type UIEventHandler,
  useLayoutEffect,
  useRef,
} from "react";
import {
  NavigationType,
  useLocation,
  useNavigationType,
} from "react-router-dom";

const positions = new Map<string, number>();

export function useMainScroll(): {
  ref: RefObject<HTMLElement | null>;
  onScroll: UIEventHandler<HTMLElement>;
} {
  const ref = useRef<HTMLElement | null>(null);
  const { key, pathname } = useLocation();
  const navigationType = useNavigationType();
  const shownPath = useRef<string | null>(null);

  useLayoutEffect(() => {
    const main = ref.current;
    if (main === null) return;
    if (navigationType === NavigationType.Pop) {
      main.scrollTop = positions.get(key) ?? 0;
    } else {
      if (shownPath.current !== pathname) main.scrollTop = 0;
      positions.set(key, main.scrollTop);
    }
    shownPath.current = pathname;
  }, [key, pathname, navigationType]);

  const onScroll: UIEventHandler<HTMLElement> = (event) => {
    positions.set(key, event.currentTarget.scrollTop);
  };
  return { ref, onScroll };
}
