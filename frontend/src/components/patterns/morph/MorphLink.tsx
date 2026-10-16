import type { MouseEvent, MouseEventHandler, ReactElement } from "react";
import { Link, type LinkProps } from "react-router-dom";

import {
  type MorphOptions,
  useMorphNavigate,
} from "@/components/patterns/morph/use-morph-navigate";

type IntentOptions = MorphOptions & { onIntent?: () => void };

function isPlainClick(event: MouseEvent): boolean {
  return (
    event.button === 0 &&
    !event.defaultPrevented &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey
  );
}

export function useMorphLinkProps(
  to: string,
  { onIntent, ...options }: IntentOptions = {},
): {
  onClick: MouseEventHandler<HTMLAnchorElement>;
  onPointerEnter?: () => void;
  onFocus?: () => void;
} {
  const morphTo = useMorphNavigate();
  const onClick: MouseEventHandler<HTMLAnchorElement> = (event) => {
    if (!isPlainClick(event)) return;
    event.preventDefault();
    void morphTo(to, options);
  };
  return {
    onClick,
    ...(onIntent !== undefined && {
      onPointerEnter: onIntent,
      onFocus: onIntent,
    }),
  };
}

export function MorphLink({
  to,
  ready,
  onIntent,
  ...props
}: Readonly<LinkProps & { to: string } & IntentOptions>): ReactElement {
  const morph = useMorphLinkProps(to, {
    ...(ready !== undefined && { ready }),
    ...(onIntent !== undefined && { onIntent }),
  });
  return <Link to={to} {...props} {...morph} />;
}
