#!/usr/bin/env python3
"""A release's checks and its draft notes, for .github/workflows/release.yml.

  release_notes.py check <tag>                  the tag is v<version>, and the chart's version and appVersion are both it
  release_notes.py notes <tag> <date> <owner>   the notes of the release published on <date> (YYYY-MM-DD), or of a
                                                draft when <date> is unpublished
  release_notes.py published <date>             the notes on stdin, dated from the release's publication on <date>
"""

import datetime
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHART = "deploy/helm/axonia/Chart.yaml"
COMPOSE = "deploy/compose.yaml"
OLDEST_POSTGRES = "backend/core/src/test/java/dev/axonia/DedicatedPostgresContainer.java"
CHART_CHECK = "deploy/helm/check.sh"
TAG = re.compile(r"^v(?P<version>(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*))$")
UNPUBLISHED = "unpublished"
LICENCE_CHANGE = re.compile(r"(?m)^It becomes available under the Apache License, Version 2\.0, on [^\r\n]*")


def version_of(tag):
    match = TAG.match(tag)
    if match is None:
        raise ValueError(tag + " is not a release tag: v<major>.<minor>.<patch>")
    return match.group("version")


def chart_field(chart, name):
    match = re.search(r"(?m)^" + name + r":\s*\"?([^\"\s#]+)\"?\s*(#.*)?$", chart)
    if match is None:
        raise ValueError(CHART + " has no " + name)
    return match.group(1)


def tag_problems(tag, chart):
    version = version_of(tag)
    return [
        CHART + " " + field + " is " + chart_field(chart, field) + ", not " + version
        + ": raise it in the commit the tag names"
        for field in ("version", "appVersion")
        if chart_field(chart, field) != version
    ]


def conversion_date(published):
    try:
        return published.replace(year=published.year + 2)
    except ValueError:
        return datetime.date(published.year + 2, 3, 1)


def licence_change(published):
    if published is None:
        return "It becomes available under the Apache License, Version 2.0, on the second anniversary of its publication."
    return ("It becomes available under the Apache License, Version 2.0, on " + conversion_date(published).isoformat()
            + ", the second anniversary of its release on " + published.isoformat() + ".")


def dated(notes_text, published):
    if not LICENCE_CHANGE.search(notes_text):
        raise ValueError("the notes state no date the release becomes Apache-2.0")
    return LICENCE_CHANGE.sub(lambda _: licence_change(published), notes_text, count=1)


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


def notes(tag, published, owner, chart, compose, container, chart_check):
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


def main(argv):
    if len(argv) == 2 and argv[0] == "check":
        problems = tag_problems(argv[1], read(CHART))
        for problem in problems:
            sys.stderr.write(problem + "\n")
        return 1 if problems else 0
    if len(argv) == 4 and argv[0] == "notes":
        published = None if argv[2] == UNPUBLISHED else datetime.date.fromisoformat(argv[2])
        sys.stdout.write(notes(argv[1], published, argv[3], read(CHART), read(COMPOSE), read(OLDEST_POSTGRES),
                               read(CHART_CHECK)))
        return 0
    if len(argv) == 2 and argv[0] == "published":
        sys.stdout.write(dated(sys.stdin.read(), datetime.date.fromisoformat(argv[1])))
        return 0
    sys.stderr.write(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
