package co.nitida.motorcycles.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndWorkshopId(UUID id, UUID workshopId);

    boolean existsByIdAndWorkshopIdAndErasedAtIsNull(UUID id, UUID workshopId);

    @Query("""
            select c from Customer c
            where c.workshopId = :workshopId and c.erasedAt is null
              and (lower(c.fullName) like lower(concat('%', :q, '%')) or c.phone like concat('%', :q, '%'))
            order by c.fullName""")
    List<Customer> search(@Param("workshopId") UUID workshopId, @Param("q") String q, Pageable page);
}
