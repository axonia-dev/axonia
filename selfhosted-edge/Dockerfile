# The Self-hosted edition's edge and the app it serves, built from the repository root:
#   docker build -f selfhosted-edge/Dockerfile .
# Parity builds pin it to a digest.
ARG NODE_IMAGE=node:24-slim

# The open frontend, built as the Self-hosted edition, with a compressed sibling beside every file the edge serves
# compressed. The bundle is the same files for every platform, so this stage runs on the builder's own and only the
# runtime stage below is built once per platform of a multi-platform image.
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS app
ENV CI=true COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable
WORKDIR /build/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
# vite.selfhosted.config.ts signs its dev server in through edge/src/auth.ts, which it loads whatever the command.
COPY edge/src/auth.ts edge/src/sealed-cookie.ts ../edge/src/
RUN pnpm run build:selfhosted
COPY selfhosted-edge/src/precompress.ts selfhosted-edge/src/precompress-main.ts ../selfhosted-edge/src/
RUN node ../selfhosted-edge/src/precompress-main.ts dist-selfhosted

# The edge runs its TypeScript as written, with Node alone: no build, and no package installed.
FROM ${NODE_IMAGE} AS runtime
RUN groupadd --system --gid 10001 axonia \
	&& useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin axonia
# selfhosted-edge/src/shared.ts names the four shared modules by their place beside it.
COPY edge/src/auth.ts edge/src/sealed-cookie.ts edge/src/csp.ts edge/src/edge-gate.ts /srv/edge/edge/src/
COPY selfhosted-edge/package.json /srv/edge/selfhosted-edge/
COPY selfhosted-edge/src/ /srv/edge/selfhosted-edge/src/
COPY --from=app /build/frontend/dist-selfhosted/ /srv/app/
WORKDIR /srv/edge/selfhosted-edge
USER 10001:10001
ENV STATIC_DIR=/srv/app
EXPOSE 8080
CMD ["node", "src/main.ts"]
