package co.nitida.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mailpit is not required: a tiny SMTP stands in for the dialogue. */
class SmtpMailboxTest {

    @Test
    void aMessageIsDeliveredAndARejectionFailsClosed() throws Exception {
        var transcript = new ArrayList<String>();
        try (var server = new ServerSocket(0)) {
            var accepted = new Thread(() -> talk(server, transcript, false));
            accepted.start();
            new SmtpMailbox("127.0.0.1", server.getLocalPort(), "no-reply@nitida.local")
                    .send("persona@taller.test", "Invitación", "línea\n.no es el fin\nTOKEN");
            accepted.join(5_000);
        }
        assertThat(transcript).contains("RCPT TO:<persona@taller.test>", "Subject: Invitación", "..no es el fin", "TOKEN");

        try (var server = new ServerSocket(0)) {
            var accepted = new Thread(() -> talk(server, new ArrayList<>(), true));
            accepted.start();
            assertThatThrownBy(() -> new SmtpMailbox("127.0.0.1", server.getLocalPort(), "no-reply@nitida.local")
                    .send("persona@taller.test", "Hola", "cuerpo")).isInstanceOf(IllegalStateException.class);
            accepted.join(5_000);
        }
    }

    private static void talk(ServerSocket server, List<String> transcript, boolean rejectRecipient) {
        try (var socket = server.accept();
             var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            reply(out, "220 ready");
            String line;
            while ((line = in.readLine()) != null) {
                transcript.add(line);
                if (line.startsWith("EHLO")) {
                    reply(out, "250-local");
                    reply(out, "250 OK");
                } else if (line.startsWith("RCPT") && rejectRecipient) {
                    reply(out, "550 no");
                } else if (line.equals("DATA")) {
                    reply(out, "354 go");
                } else if (line.equals(".")) {
                    reply(out, "250 queued");
                } else if (line.equals("QUIT")) {
                    reply(out, "221 bye");
                    return;
                } else {
                    reply(out, "250 OK");
                }
            }
        } catch (Exception ignored) {
            // the client closes after a 550
        }
    }

    private static void reply(OutputStreamWriter out, String line) throws Exception {
        out.write(line + "\r\n");
        out.flush();
    }
}
