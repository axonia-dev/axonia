import {
  Activity,
  Book,
  Bookmark,
  Calendar,
  GraduationCap,
  Lightbulb,
  List,
  Megaphone,
  Settings2,
  Sprout,
  Tags,
  User,
  Zap,
} from "lucide-react";
import type { ElementType } from "react";

// Compact tuple form for the community nav (see the note in CommunityShell):
// [label, path (relative to base), icon component, options]
interface ItemOptions {
  end?: boolean;
  /** Paths, relative to base, whose pages belong under this item. */
  owns?: readonly string[];
}
export type ItemSpec = [string, string, ElementType, ItemOptions?];
// [group label, group icon component, items]
export type GroupSpec = [string, ElementType, ItemSpec[]];

/**
 * The one group every organization adds to the community shell: a personal
 * "Me" surface (impact, expertise, saved - every member has these, no
 * permission gate). Organization administration lives behind the account
 * menu's Organization settings, not in this rail.
 */
function meGroupSpec(): GroupSpec {
  return [
    "Me",
    User,
    [
      ["My impact", "me/impact", Zap],
      ["My expertise", "me/expertise", Lightbulb],
      ["Saved", "me/saved", Bookmark],
    ],
  ];
}

// The management-stewardship access flags the "Manage" group reads.
export type CommunityNavAccess = { curation: boolean; health: boolean };

/**
 * The full, ordered community rail below "Overview": community content
 * (Engage, Knowledge), then the member's own slice (Me), then stewardship
 * (Manage, when access is granted). Assembled here (rather than in the shell
 * component) so the branching stays out of CommunityShell's eslint
 * `complexity` budget.
 */
export function communityGroupSpecs(access: CommunityNavAccess): GroupSpec[] {
  const groups: GroupSpec[] = [
    [
      "Engage",
      Megaphone,
      [
        ["Schedule", "schedule", Calendar, { owns: ["sessions", "series"] }],
        ["Backlog", "backlog", List],
      ],
    ],
    [
      "Knowledge",
      GraduationCap,
      [
        ["Library", "library", Book],
        ["Expertise", "expertise", Lightbulb, { end: true }],
        ["Learning", "learning", Sprout],
      ],
    ],
    meGroupSpec(),
  ];

  const manageItems: ItemSpec[] = [];
  if (access.curation)
    manageItems.push(["Curation", "expertise/curation", Tags]);
  if (access.health) manageItems.push(["Health", "health", Activity]);
  if (manageItems.length > 0) groups.push(["Manage", Settings2, manageItems]);

  return groups;
}
