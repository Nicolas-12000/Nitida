package co.nitida;

import co.nitida.shared.crypto.KeyCipher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class SharedConfig {
    /** Encrypts secrets at rest: workshop signing keys and users' TOTP secrets. */
    @Bean
    KeyCipher keyCipher(@Value("${nitida.signing.kek}") String kek, @Value("${nitida.signing.kek-version:1}") int version,
                        @Value("${nitida.signing.kek-previous:}") String previous,
                        @Value("${nitida.signing.kek-previous-version:0}") int previousVersion) {
        return new KeyCipher(kek, version, previous, previousVersion);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
