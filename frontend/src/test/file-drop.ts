/** A minimal DataTransfer stand-in carrying a single file, as React Aria reads it. */
export function fileTransfer(file: File) {
  return {
    effectAllowed: "all",
    items: [{ kind: "file", type: file.type, getAsFile: () => file }],
    types: ["Files"],
  };
}
