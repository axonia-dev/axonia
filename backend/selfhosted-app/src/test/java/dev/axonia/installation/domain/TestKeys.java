package dev.axonia.installation.domain;

/** Settings keys for tests, each 32 readable bytes in base64, and the identifiers HKDF derives for them. */
public final class TestKeys {

    public static final String CURRENT = "dGVzdC1sb2NhbC1zZXR0aW5ncy1rZXktMzJieXRlcyE=";

    public static final String CURRENT_ID = "bff04208";

    public static final String PREVIOUS = "cHJldmlvdXMtc2V0dGluZ3Mta2V5LTMyLWJ5dGVzISE=";

    public static final String PREVIOUS_ID = "e4caaa0c";

    private TestKeys() {
    }

    public static SettingsKeys current() {

        return SettingsKeys.parse(CURRENT, "");
    }

    public static SettingsKeys currentWithPrevious() {

        return SettingsKeys.parse(CURRENT, PREVIOUS);
    }

    public static SettingsKeys previousOnly() {

        return SettingsKeys.parse(PREVIOUS, "");
    }
}
