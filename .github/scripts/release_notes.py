#!/usr/bin/env python3
"""A release's version and its notes, for .github/workflows/release.yml.

  release_notes.py version [<requested>]        the version to release from the checked-out commit: <requested>, or the
                                                latest release's patch raised by one
  release_notes.py notes <tag> <date> <owner>   the notes of the release published on <date> (YYYY-MM-DD)
"""

import datetime
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = "deploy/compose.yaml"
OLDEST_POSTGRES = "backend/core/src/test/java/dev/axonia/DedicatedPostgresContainer.java"
CHART_CHECK = "deploy/helm/check.sh"
TAG = re.compile(r"^v(?P<version>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*))$")


def version_of(tag):
    match = TAG.match(tag)
    if match is None:
        raise ValueError(tag + " is not a release tag: v<major>.<minor>.<patch>")
    return match.group("version")


def parts(version):
    return tuple(int(part) for part in version.split("."))


def release_version(requested, tags, head_tags):
    """requested is what the person releasing typed, empty for the next patch; tags are the repository's tags and
    head_tags the ones on the commit being released, which a rerun of a release that tagged it already finds."""
    released = sorted((version_of(tag) for tag in tags if TAG.match(tag)), key=parts)
    on_head = sorted((version_of(tag) for tag in head_tags if TAG.match(tag)), key=parts)
    if requested:
        version = version_of("v" + requested.removeprefix("v"))
        if version in on_head:
            return version
        if version in released:
            raise ValueError(version + " is released already, from another commit. A version is released once.")
        if released and parts(version) < parts(released[-1]):
            raise ValueError(version + " is older than the latest release, " + released[-1] + ".")
        return version
    if on_head:
        return on_head[-1]
    if not released:
        raise ValueError("Nothing is released yet: name the first version.")
    major, minor, patch = parts(released[-1])
    return str(major) + "." + str(minor) + "." + str(patch + 1)


def conversion_date(published):
    try:
        return published.replace(year=published.year + 2)
    except ValueError:
        return datetime.date(published.year + 2, 3, 1)


def licence_change(published):
    return ("It becomes available under the Apache License, Version 2.0, on " + conversion_date(published).isoformat()
            + ", the second anniversary of its release on " + published.isoformat() + ".")


def postgres_major(compose):
    match = re.search(r"(?m)^\s*image:\s*postgres:(\d+)", compose)
    if match is None:
        raise ValueError(COMPOSE + " runs no postgres:<major> image")
    return match.group(1)


def oldest_postgres_major(container):
    match = re.search(r'OLDEST_SUPPORTED\s*=\s*"postgres:(\d+)"', container)
    if match is None:
        raise ValueError(OLDEST_POSTGRES + " names no OLDEST_SUPPORTED postgres:<major>")
    return match.group(1)


def postgres_line(oldest, shipped):
    if int(oldest) > int(shipped):
        raise ValueError("the tests' oldest Postgres " + oldest + " is newer than the " + shipped + " compose ships")
    if oldest == shipped:
        return "PostgreSQL " + shipped
    joiner = " or " if int(shipped) - int(oldest) == 1 else " to "
    return "PostgreSQL " + oldest + joiner + shipped + "; compose ships " + shipped


def kubernetes_range(chart_check):
    match = re.search(r"for version in (\d+\.\d+)\.\d+ (\d+\.\d+)\.\d+; do", chart_check)
    if match is None:
        raise ValueError(CHART_CHECK + " validates against no oldest and newest Kubernetes")
    return match.group(1) + " to " + match.group(2)


def notes(tag, published, owner, compose, container, chart_check):
    version = version_of(tag)
    registry = "ghcr.io/" + owner.lower()
    return "\n".join([
        "## Licence",
        "",
        "This release is licensed under the Functional Source License, Version 1.1, ALv2 Future License (`LICENSE`).",
        licence_change(published),
        "",
        "## Install",
        "",
        "- `" + registry + "/axonia-backend:" + version + "`",
        "- `" + registry + "/axonia-edge:" + version + "`",
        "- The Helm chart `oci://" + registry + "/charts/axonia`, version " + version,
        "",
        "Docker Compose runs these images with `AXONIA_VERSION=" + version + "` in `deploy/.env`.",
        "",
        "## Tested with",
        "",
        "- " + postgres_line(oldest_postgres_major(container), postgres_major(compose)),
        "- Kubernetes " + kubernetes_range(chart_check),
        "",
        "## Upgrading",
        "",
        "Read `docs/selfhosted/upgrading.md` and the notes of every release since the one installed.",
        "",
    ])


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def git_tags(*arguments):
    return subprocess.run(["git", "tag", "--list", *arguments], cwd=ROOT, check=True, capture_output=True,
                          text=True).stdout.split()


def main(argv):
    if argv[:1] == ["version"] and len(argv) <= 2:
        try:
            print(release_version(argv[1] if len(argv) == 2 else "", git_tags(), git_tags("--points-at", "HEAD")))
        except ValueError as problem:
            sys.stderr.write(str(problem) + "\n")
            return 1
        return 0
    if len(argv) == 4 and argv[0] == "notes":
        sys.stdout.write(notes(argv[1], datetime.date.fromisoformat(argv[2]), argv[3], read(COMPOSE),
                               read(OLDEST_POSTGRES), read(CHART_CHECK)))
        return 0
    sys.stderr.write(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
