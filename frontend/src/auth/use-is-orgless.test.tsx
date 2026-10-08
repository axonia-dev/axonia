import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it } from "vitest";

import { useIsOrgless } from "@/auth/use-is-orgless";
import { EditionProvider } from "@/edition-context";
import { selfHostedEdition } from "@/selfhosted/edition";
import {
  signInOrgless,
  signInScopedTo,
  signOutOfTest,
} from "@/test/access-token";

afterEach(signOutOfTest);

describe("useIsOrgless", () => {
  it("is true when the token carries no organization", () => {
    // given
    signInOrgless();

    // when
    const { result } = renderHook(() => useIsOrgless());

    // then
    expect(result.current).toBe(true);
  });

  it("is false when the token carries an organization", () => {
    // given
    signInScopedTo("acme");

    // when
    const { result } = renderHook(() => useIsOrgless());

    // then
    expect(result.current).toBe(false);
  });

  it("follows the token registered after a scope switch", () => {
    // given
    signInOrgless();
    const { result, rerender } = renderHook(() => useIsOrgless());

    // when
    signInScopedTo("acme");
    rerender();

    // then
    expect(result.current).toBe(false);
  });

  it("is false for an installation's session, whose token never names an organization", () => {
    // given
    signInOrgless();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <EditionProvider edition={selfHostedEdition}>{children}</EditionProvider>
    );

    // when
    const { result } = renderHook(() => useIsOrgless(), { wrapper });

    // then
    expect(result.current).toBe(false);
  });
});
