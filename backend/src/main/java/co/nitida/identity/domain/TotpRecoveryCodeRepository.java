package co.nitida.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, UUID> {
    Optional<TotpRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);
}
