package dev.axonia.installation.domain;

/** A mail server field: its name in the API and the history, and the variable that locks it. */
public enum MailField {

    HOST("host", "AXONIA_MAIL_HOST"), PORT("port", "AXONIA_MAIL_PORT"), SECURITY("security",
            "AXONIA_MAIL_SECURITY"), USERNAME("username", "AXONIA_MAIL_USERNAME"), PASSWORD("password",
                    "AXONIA_MAIL_PASSWORD"), FROM_ADDRESS("fromAddress",
                            "AXONIA_MAIL_FROM_ADDRESS"), FROM_NAME("fromName", "AXONIA_MAIL_FROM_NAME");

    private final String apiName;

    private final String variable;

    MailField(String apiName, String variable) {

        this.apiName = apiName;
        this.variable = variable;
    }

    String apiName() {

        return apiName;
    }

    String variable() {

        return variable;
    }
}
