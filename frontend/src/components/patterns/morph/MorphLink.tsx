import type {
  FocusEventHandler,
  MouseEvent,
  MouseEventHandler,
  PointerEventHandler,
  ReactElement,
} from "react";
import { Link, type LinkProps } from "react-router-dom";

import {
  type MorphOptions,
  useMorphNavigate,
} from "@/components/patterns/morph/use-morph-navigate";

type Anchor = HTMLAnchorElement;

interface CallerHandlers {
  onClick?: MouseEventHandler<Anchor> | undefined;
  onPointerEnter?: PointerEventHandler<Anchor> | undefined;
  onFocus?: FocusEventHandler<Anchor> | undefined;
}

type IntentOptions = MorphOptions &
  CallerHandlers & {
    onIntent?: (() => void) | undefined;
    reloadDocument?: boolean | undefined;
  };

function isPlainClick(event: MouseEvent<Anchor>): boolean {
  return (
    event.button === 0 &&
    !event.defaultPrevented &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey
  );
}

function opensElsewhere(event: MouseEvent<Anchor>): boolean {
  const { target } = event.currentTarget;
  return target !== "" && target !== "_self";
}

function withIntent<E>(
  caller: ((event: E) => void) | undefined,
  onIntent: (() => void) | undefined,
): ((event: E) => void) | undefined {
  if (caller === undefined && onIntent === undefined) return undefined;
  return (event) => {
    caller?.(event);
    onIntent?.();
  };
}

export function useMorphLinkProps(
  to: string,
  {
    onIntent,
    onClick: callerClick,
    onPointerEnter,
    onFocus,
    reloadDocument,
    ...options
  }: IntentOptions = {},
): {
  onClick: MouseEventHandler<Anchor>;
  onPointerEnter?: PointerEventHandler<Anchor>;
  onFocus?: FocusEventHandler<Anchor>;
} {
  const morphTo = useMorphNavigate();
  const onClick: MouseEventHandler<Anchor> = (event) => {
    callerClick?.(event);
    if (!isPlainClick(event) || opensElsewhere(event) || reloadDocument) return;
    event.preventDefault();
    void morphTo(to, options);
  };
  const pointer = withIntent(onPointerEnter, onIntent);
  const focus = withIntent(onFocus, onIntent);
  return {
    onClick,
    ...(pointer !== undefined && { onPointerEnter: pointer }),
    ...(focus !== undefined && { onFocus: focus }),
  };
}

export function MorphLink({
  to,
  ready,
  onIntent,
  ...props
}: Readonly<LinkProps & { to: string } & IntentOptions>): ReactElement {
  const morph = useMorphLinkProps(to, {
    ready,
    onIntent,
    onClick: props.onClick,
    onPointerEnter: props.onPointerEnter,
    onFocus: props.onFocus,
    replace: props.replace,
    state: props.state,
    reloadDocument: props.reloadDocument,
  });
  return <Link to={to} {...props} {...morph} />;
}
