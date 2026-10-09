import datetime
import unittest

import release_notes

TAGS = ["v0.1.0", "v0.2.0", "v0.2.1", "v0.10.0-rc.1", "nightly"]
COMPOSE = "services:\n  postgres:\n    image: postgres:18\n"
CONTAINER = '    public static final String OLDEST_SUPPORTED = "postgres:17";\n'
CHART_CHECK = "\tfor version in 1.29.0 1.37.0; do\n"


class VersionTest(unittest.TestCase):
    def test_nothing_requested_raises_the_latest_releases_patch(self):
        self.assertEqual("0.2.2", release_notes.release_version("", TAGS, []))

    def test_the_latest_release_is_compared_by_number_not_by_text(self):
        self.assertEqual("0.10.1", release_notes.release_version("", ["v0.9.0", "v0.10.0"], []))

    def test_a_requested_version_is_released_with_or_without_its_v(self):
        self.assertEqual("0.3.0", release_notes.release_version("0.3.0", TAGS, []))
        self.assertEqual("1.0.0", release_notes.release_version("v1.0.0", TAGS, []))

    def test_a_rerun_releases_the_version_its_first_run_tagged_on_the_commit(self):
        self.assertEqual("0.2.1", release_notes.release_version("", TAGS, ["v0.2.1"]))
        self.assertEqual("0.2.1", release_notes.release_version("0.2.1", TAGS, ["v0.2.1"]))

    def test_a_version_released_from_another_commit_is_refused(self):
        with self.assertRaisesRegex(ValueError, "0.2.0 is released already"):
            release_notes.release_version("0.2.0", TAGS, [])

    def test_a_version_older_than_the_latest_release_is_refused(self):
        with self.assertRaisesRegex(ValueError, "older than the latest release, 0.2.1"):
            release_notes.release_version("0.1.5", TAGS, [])

    def test_the_first_release_must_be_named(self):
        with self.assertRaisesRegex(ValueError, "name the first version"):
            release_notes.release_version("", ["nightly"], [])
        self.assertEqual("0.1.0", release_notes.release_version("0.1.0", [], []))

    def test_a_version_that_is_not_a_release_is_refused(self):
        for requested in ["0.2", "0.2.0-rc.1", "01.2.0", "release-0.2.0"]:
            with self.assertRaises(ValueError):
                release_notes.release_version(requested, TAGS, [])


class DatesTest(unittest.TestCase):
    def test_the_licence_converts_on_the_second_anniversary(self):
        self.assertEqual(datetime.date(2028, 10, 6), release_notes.conversion_date(datetime.date(2026, 10, 6)))

    def test_a_release_on_the_29th_of_february_converts_on_the_1st_of_march(self):
        self.assertEqual(datetime.date(2030, 3, 1), release_notes.conversion_date(datetime.date(2028, 2, 29)))


class NotesTest(unittest.TestCase):
    def test_name_the_conversion_date_the_images_the_chart_postgres_and_kubernetes(self):
        text = release_notes.notes("v0.2.0", datetime.date(2026, 10, 6), "Axonia-Dev", COMPOSE, CONTAINER, CHART_CHECK)
        self.assertIn("on 2028-10-06, the second anniversary of its release on 2026-10-06.", text)
        self.assertIn("- `ghcr.io/axonia-dev/axonia-backend:0.2.0`", text)
        self.assertIn("- `ghcr.io/axonia-dev/axonia-edge:0.2.0`", text)
        self.assertIn("- The Helm chart `oci://ghcr.io/axonia-dev/charts/axonia`, version 0.2.0", text)
        self.assertIn("- PostgreSQL 17 or 18; compose ships 18", text)
        self.assertIn("- Kubernetes 1.29 to 1.37", text)

    def test_a_compose_file_without_postgres_is_refused(self):
        with self.assertRaises(ValueError):
            release_notes.postgres_major("services: {}\n")

    def test_the_postgres_line_spans_the_oldest_tested_major_to_the_one_compose_ships(self):
        self.assertEqual("PostgreSQL 18", release_notes.postgres_line("18", "18"))
        self.assertEqual("PostgreSQL 17 or 18; compose ships 18", release_notes.postgres_line("17", "18"))
        self.assertEqual("PostgreSQL 17 to 19; compose ships 19", release_notes.postgres_line("17", "19"))
        with self.assertRaises(ValueError):
            release_notes.postgres_line("19", "18")
        with self.assertRaises(ValueError):
            release_notes.oldest_postgres_major("class DedicatedPostgresContainer {}\n")


if __name__ == "__main__":
    unittest.main()
