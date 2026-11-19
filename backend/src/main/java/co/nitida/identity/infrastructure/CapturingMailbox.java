package co.nitida.identity.infrastructure;

import co.nitida.identity.application.Mailbox;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Test and local stand-in: keeps messages in memory instead of opening SMTP. */
@Component
@ConditionalOnProperty(name = "nitida.mail.mode", havingValue = "capture")
public class CapturingMailbox implements Mailbox {
    public record Message(String to, String subject, String text) {}

    private final List<Message> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Message(to, subject, text));
    }

    public List<Message> sent() { return sent; }

    public Message last() { return sent.get(sent.size() - 1); }
}
