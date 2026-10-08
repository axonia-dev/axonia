import type {
  SaveableRef,
  SavedItem,
  SaveState,
} from "@/features/bookmarks/domain/saved";

export interface SaveGateway {
  getState(ref: SaveableRef): Promise<SaveState>;
  save(ref: SaveableRef): Promise<SaveState>;
  unsave(ref: SaveableRef): Promise<void>;
  // `true` marks the save as helpful; `null` clears the mark back to neutral.
  markHelpful(artifactId: number, helped: boolean | null): Promise<SaveState>;
  listMine(): Promise<SavedItem[]>;
}
