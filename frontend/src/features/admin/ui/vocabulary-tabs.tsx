import type { LucideIcon } from "lucide-react";
import { LayoutList, Library, SmilePlus, Tag } from "lucide-react";
import type { ComponentType } from "react";

import {
  VOCABULARY_TAB_SPECS,
  type VocabularyTabPath,
} from "@/features/admin/ui/vocabulary";
import { CommunityKindManager } from "@/features/community/ui/CommunityKindManager";
import { SectionTypeManager } from "@/features/events/ui/SectionTypeManager";
import { LibraryTypeManager } from "@/features/knowledge/ui/LibraryTypeManager";
import { ReactionManager } from "@/features/recognition/ui/ReactionManager";

/** The visuals and manager component each tab binds to, keyed by the path {@link
 *  VOCABULARY_TAB_SPECS} declares. Typed as a `Record` over {@link VocabularyTabPath} so
 *  a tab added to the pure spec list without a matching entry here fails to compile
 *  instead of rendering with a missing icon or manager.
 *
 *  Each `icon` is the one that destination carried when it was a rail item of its own:
 *  the tab is where a member who has been clicking it for months finds it again. */
const VOCABULARY_TAB_VISUALS: Record<
  VocabularyTabPath,
  { icon: LucideIcon; Manager: ComponentType }
> = {
  "community-types": { icon: Tag, Manager: CommunityKindManager },
  "library-types": { icon: Library, Manager: LibraryTypeManager },
  "section-types": { icon: LayoutList, Manager: SectionTypeManager },
  reactions: { icon: SmilePlus, Manager: ReactionManager },
};

export interface VocabularyTab {
  path: string;
  label: string;
  icon: LucideIcon;
  permission: string;
  Manager: ComponentType;
}

export const VOCABULARY_TABS: ReadonlyArray<VocabularyTab> =
  VOCABULARY_TAB_SPECS.map((spec) => ({
    ...spec,
    ...VOCABULARY_TAB_VISUALS[spec.path],
  }));
