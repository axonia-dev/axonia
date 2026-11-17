import { createContext } from "react";

export interface MovedNote {
  noteId: number;
  meetingOn: string;
}

/** A note whose day changed moves to another card; the logbook brings focus along. */
export const FollowNote = createContext<(moved: MovedNote) => void>(() => {});
