package dev.axonia.communication;

/** A username and a password, set together or not at all. The password never reaches {@link #toString()}. */
public record MailCredentials(String username, String password) {

    public MailCredentials {
        if (username == null || username.isBlank() || MailServerSettings.hasControlCharacter(username)) {
            throw new IllegalArgumentException("username must be set, on one line");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must be set");
        }
    }

    @Override
    public String toString() {

        return "MailCredentials[username=" + username + ", password=********]";
    }
}
