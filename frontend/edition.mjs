// Runs `<name>:cloud` in a checkout holding src/cloud/ and `<name>:selfhosted` in one without it,
// so the unsuffixed script names work in both the private and the public repository.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";

const [name, ...args] = process.argv.slice(2);
const edition = existsSync("src/cloud") ? "cloud" : "selfhosted";
const script = `${name}:${edition}`;
const { scripts } = JSON.parse(readFileSync("package.json", "utf8"));

if (!Object.hasOwn(scripts, script)) {
  console.error(`No ${edition} script for ${name} in this checkout.`);
  process.exit(1);
}

const pnpm = process.env.npm_execpath ?? "pnpm";
const runsOnNode = /\.[cm]?js$/.test(pnpm);
const result = spawnSync(
  runsOnNode ? process.execPath : pnpm,
  [...(runsOnNode ? [pnpm] : []), "run", script, ...args],
  { stdio: "inherit", shell: !runsOnNode && process.platform === "win32" },
);

if (result.signal) {
  process.kill(process.pid, result.signal);
}
process.exit(result.status ?? 1);
