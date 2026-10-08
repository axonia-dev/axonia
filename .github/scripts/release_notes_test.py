import datetime
import unittest

import release_notes

CHART = 'apiVersion: v2\nname: axonia\n# The release.\nversion: 0.2.0\nappVersion: "0.2.0"\nkubeVersion: ">=1.29.0-0"\n'


class TagTest(unittest.TestCase):
    def test_a_tag_naming_the_charts_version_passes(self):
        self.assertEqual([], release_notes.tag_problems("v0.2.0", CHART))

    def test_a_tag_naming_another_version_names_both_fields(self):
        self.assertEqual(
            [
                "deploy/helm/axonia/Chart.yaml version is 0.2.0, not 0.3.0: raise it in the commit the tag names",
                "deploy/helm/axonia/Chart.yaml appVersion is 0.2.0, not 0.3.0: raise it in the commit the tag names",
            ],
            release_notes.tag_problems("v0.3.0", CHART),
        )

    def test_a_tag_that_is_not_a_release_is_refused(self):
        for tag in ["0.2.0", "v0.2", "v0.2.0-rc.1", "v01.2.0", "release-0.2.0"]:
            with self.assertRaises(ValueError):
                release_notes.version_of(tag)


class DatesTest(unittest.TestCase):
    def test_the_licence_converts_on_the_second_anniversary(self):
        self.assertEqual(datetime.date(2028, 10, 6), release_notes.conversion_date(datetime.date(2026, 10, 6)))

    def test_a_release_on_the_29th_of_february_converts_on_the_1st_of_march(self):
        self.assertEqual(datetime.date(2030, 3, 1), release_notes.conversion_date(datetime.date(2028, 2, 29)))


class NotesTest(unittest.TestCase):
    def test_name_the_conversion_date_the_images_the_chart_postgres_and_kubernetes(self):
        text = release_notes.notes(
            "v0.2.0",
            datetime.date(2026, 10, 6),
            "Axonia-Dev",
            CHART,
            "services:\n  postgres:\n    image: postgres:18\n",
            '    public static final String OLDEST_SUPPORTED = "postgres:17";\n',
            "\tfor version in 1.29.0 1.37.0; do\n",
        )
        self.assertIn("on 2028-10-06, the second anniversary of its release on 2026-10-06.", text)
        self.assertIn("- `ghcr.io/axonia-dev/axonia-backend:0.2.0`", text)
        self.assertIn("- `ghcr.io/axonia-dev/axonia-edge:0.2.0`", text)
        self.assertIn("- The Helm chart `oci://ghcr.io/axonia-dev/charts/axonia`, version 0.2.0", text)
        self.assertIn("- PostgreSQL 17 or 18; compose ships 18", text)
        self.assertIn("- Kubernetes 1.29 to 1.37", text)

    def test_a_draft_states_the_change_without_a_date_and_publication_dates_it(self):
        draft = release_notes.notes(
            "v0.2.0", None, "axonia-dev", CHART, "services:\n  postgres:\n    image: postgres:18\n",
            '    public static final String OLDEST_SUPPORTED = "postgres:17";\n', "\tfor version in 1.29.0 1.37.0; do\n")
        self.assertIn("It becomes available under the Apache License, Version 2.0, on the second anniversary of its "
                      "publication.\n", draft)
        body = draft + "\n## What's Changed\n* feat: a thing by @someone\n"
        published = release_notes.dated(body, datetime.date(2026, 10, 9))
        self.assertEqual(
            body.replace("on the second anniversary of its publication.",
                         "on 2028-10-09, the second anniversary of its release on 2026-10-09."),
            published,
        )
        self.assertEqual(published.replace("2026-10-09", "2026-10-12").replace("2028-10-09", "2028-10-12"),
                         release_notes.dated(published, datetime.date(2026, 10, 12)))

    def test_notes_without_the_licence_change_are_refused(self):
        with self.assertRaises(ValueError):
            release_notes.dated("## What's Changed\n", datetime.date(2026, 10, 9))

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
