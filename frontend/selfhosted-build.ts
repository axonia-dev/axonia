import { renameSync, rmSync } from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";

import type { Plugin } from "vite";

export const SELF_HOSTED_MODE = "selfhosted";
export const SELF_HOSTED_OUT_DIR = "dist-selfhosted";
export const SELF_HOSTED_DOCUMENT = "selfhosted.html";

/** Vite copies public/ into every build; these files are the Cloud's alone. */
const CLOUD_PUBLIC_FILES = ["robots.txt", "sitemap.xml", "og-card.png"];

/** True for a module under src/cloud/, which no module of the open edition may load. */
export function isCloudModule(root: string, id: string): boolean {
  const file = id.split("?", 1)[0] ?? "";
  const below = relative(join(root, "src", "cloud"), file);
  return below === "" || (!below.startsWith("..") && !isAbsolute(below));
}

/** The dev server's document fallback, pointed at the open edition's entry document. */
export function selfHostedDocumentUrl(url: string): string {
  const [path, ...query] = url.split("?");
  if (path !== "/index.html") return url;
  return [`/${SELF_HOSTED_DOCUMENT}`, ...query].join("?");
}

function finishSelfHostedOutput(outDir: string): void {
  renameSync(join(outDir, SELF_HOSTED_DOCUMENT), join(outDir, "index.html"));
  for (const file of CLOUD_PUBLIC_FILES) {
    rmSync(join(outDir, file), { force: true });
  }
}

/**
 * The open edition's build, test run and dev server: refuses any module under src/cloud/ (the one
 * check that also sees a relative import), serves selfhosted.html where the Cloud serves
 * index.html, and leaves dist-selfhosted/ with an index.html and none of the Cloud's public files.
 */
export function selfHostedBuild(): Plugin {
  let root = process.cwd();
  let outDir = resolve(root, SELF_HOSTED_OUT_DIR);
  return {
    name: "axonia:selfhosted-build",
    enforce: "pre",
    configResolved(config) {
      root = config.root;
      outDir = resolve(config.root, config.build.outDir);
    },
    load(id) {
      if (isCloudModule(root, id)) {
        this.error(
          `${relative(root, id)} is Cloud code, which the Self-hosted edition does not contain.`,
        );
      }
      return null;
    },
    configureServer(server) {
      // Returned, so it runs after Vite's SPA fallback has rewritten the URL and before the
      // document is transformed.
      return () => {
        server.middlewares.use((request, _response, next) => {
          if (request.url !== undefined) {
            request.url = selfHostedDocumentUrl(request.url);
          }
          next();
        });
      };
    },
    // After a successful write only: public/ is copied before it, and a failed build leaves
    // nothing to rename.
    writeBundle() {
      finishSelfHostedOutput(outDir);
    },
  };
}
