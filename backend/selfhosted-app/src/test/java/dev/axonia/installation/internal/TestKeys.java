package dev.axonia.installation.internal;

/** Settings keys for tests, each 32 readable bytes in base64, and the identifiers HKDF derives for them. */
final class TestKeys {

    static final String CURRENT = "dGVzdC1sb2NhbC1zZXR0aW5ncy1rZXktMzJieXRlcyE=";

    static final String CURRENT_ID = "bff04208";

    static final String PREVIOUS = "cHJldmlvdXMtc2V0dGluZ3Mta2V5LTMyLWJ5dGVzISE=";

    static final String PREVIOUS_ID = "e4caaa0c";

    private TestKeys() {
    }

    static SettingsKeys current() {

        return SettingsKeys.parse(CURRENT, "");
    }

    static SettingsKeys currentWithPrevious() {

        return SettingsKeys.parse(CURRENT, PREVIOUS);
    }

    static SettingsKeys previousOnly() {

        return SettingsKeys.parse(PREVIOUS, "");
    }
}
