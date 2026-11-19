package co.nitida.workshops.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WorkshopsConfig {
    @Bean
    KeyCipher keyCipher(@Value("${nitida.signing.kek}") String kek, @Value("${nitida.signing.kek-version}") int version) {
        return new KeyCipher(kek, version);
    }
}
