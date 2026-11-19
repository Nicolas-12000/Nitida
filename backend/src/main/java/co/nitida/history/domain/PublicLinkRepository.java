package co.nitida.history.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicLinkRepository extends JpaRepository<PublicLink, UUID> {
    Optional<PublicLink> findByTokenHash(String tokenHash);
}
