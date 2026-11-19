package co.nitida.motorcycles.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimCodeRepository extends JpaRepository<ClaimCode, UUID> {
    Optional<ClaimCode> findByCodeHash(String codeHash);

    Optional<ClaimCode> findByCodeHashAndKind(String codeHash, String kind);
}
