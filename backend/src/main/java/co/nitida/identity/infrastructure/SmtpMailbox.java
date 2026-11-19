package co.nitida.identity.infrastructure;

import co.nitida.identity.application.Mailbox;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Plain SMTP for Mailpit (no auth). The message is not logged: it carries the only copy of the token. */
@Component
@ConditionalOnProperty(name = "nitida.mail.mode", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailbox implements Mailbox {
    private final String host;
    private final int port;
    private final String from;

    SmtpMailbox(@Value("${nitida.mail.host:localhost}") String host, @Value("${nitida.mail.port:1025}") int port,
                @Value("${nitida.mail.from:no-reply@nitida.local}") String from) {
        this.host = host;
        this.port = port;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3_000);
            socket.setSoTimeout(5_000);
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            read(in);
            command(out, in, "EHLO nitida.local");
            command(out, in, "MAIL FROM:<" + from + ">");
            command(out, in, "RCPT TO:<" + to + ">");
            command(out, in, "DATA");
            write(out, "From: " + from);
            write(out, "To: " + to);
            write(out, "Subject: " + subject.replace('\r', ' ').replace('\n', ' '));
            write(out, "Content-Type: text/plain; charset=UTF-8");
            write(out, "");
            for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
                write(out, line.startsWith(".") ? "." + line : line);
            }
            command(out, in, ".");
            command(out, in, "QUIT");
        } catch (IOException e) {
            throw new IllegalStateException("could not send mail", e);
        }
    }

    private static void command(BufferedWriter out, BufferedReader in, String line) throws IOException {
        write(out, line);
        read(in);
    }

    private static void write(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    /** Reads a possibly multi-line reply and fails on 4xx/5xx. The text is not kept (it can echo the recipient). */
    private static void read(BufferedReader in) throws IOException {
        String line;
        char status = '2';
        do {
            line = in.readLine();
            if (line == null || line.length() < 3) throw new IOException("smtp closed");
            status = line.charAt(0);
        } while (line.length() > 3 && line.charAt(3) == '-');
        if (status >= '4') throw new IOException("smtp rejected the message");
    }
}
