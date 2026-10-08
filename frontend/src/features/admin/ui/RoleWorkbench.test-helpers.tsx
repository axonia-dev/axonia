import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { vi } from "vitest";

import type { EditionSlots } from "@/edition";
import { RolesPage } from "@/features/admin/ui/RolesPage";
import { openSlots } from "@/open-slots";
import { inEdition } from "@/test/edition-fixtures";

/**
 * Shared by RoleWorkbench.test.tsx, RoleWorkbench.compare.test.tsx and
 * RoleWorkbench.search.test.tsx: the mocked hooks, the base path, and the render helper. Each
 * file still declares its own `vi.mock` calls, since vitest hoists those per file, and each
 * owns its own role fixture: the counts a search or a comparison reports depend on exactly how
 * many roles and permissions the fixture holds, and that arithmetic legitimately differs
 * between files.
 */
export const mockUseRoles = vi.fn();
export const updateMutate = vi.fn();
export const createMutate = vi.fn();
export const can = vi.fn();
export const mockHeldPermissions = vi.fn();

export const BASE = "/settings/organization";

export function renderAt(path: string, edition: EditionSlots = openSlots) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={`${BASE}/roles`} element={<RolesPage base={BASE} />} />
        <Route
          path={`${BASE}/roles/:roleId`}
          element={<RolesPage base={BASE} />}
        />
      </Routes>
    </MemoryRouter>,
    { wrapper: inEdition(edition) },
  );
}
