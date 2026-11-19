package co.nitida.identity.application;

/** Outbound mail. The plain token lives only in the message, never in a response or a log. */
public interface Mailbox {
    void send(String to, String subject, String text);

    /** The token is the last non-blank line, so a test can read what the person would read. */
    static String tokenOf(String text) {
        String token = "";
        for (String line : text.split("\n")) {
            if (!line.isBlank()) token = line.strip();
        }
        return token;
    }
}
