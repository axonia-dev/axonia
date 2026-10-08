import { precompress } from "./precompress.ts";

const root = process.argv[2];
if (root === undefined) {
  process.stderr.write("Usage: node src/precompress-main.ts <the built app's directory>\n");
  process.exit(2);
}
const written = await precompress(root);
process.stdout.write(
  `${JSON.stringify({ level: "info", event: "precompressed", siblings: written.length })}\n`,
);
