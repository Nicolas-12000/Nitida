package co.nitida.catalog.application;

import co.nitida.catalog.CatalogApi;
import co.nitida.catalog.SerialRejected;
import co.nitida.catalog.domain.Product;
import co.nitida.catalog.domain.ProductCode;
import co.nitida.catalog.domain.ProductRepository;
import co.nitida.catalog.domain.Serial;
import co.nitida.catalog.domain.SerialNumber;
import co.nitida.catalog.domain.SerialRepository;
import co.nitida.shared.UuidV7;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.sql.DataSource;

@Service
class CatalogApiImpl implements CatalogApi {
    private final ProductRepository products;
    private final SerialRepository serials;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JdbcClient jdbc;
    private final DataSource dataSource;

    CatalogApiImpl(ProductRepository products, SerialRepository serials, ApplicationEventPublisher events, Clock clock,
                   JdbcClient jdbc, DataSource dataSource) {
        this.products = products;
        this.serials = serials;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductInfo> findProduct(UUID workshopId, String code) {
        return products.findByWorkshopIdAndCode(workshopId, ProductCode.of(code).value()).map(CatalogApiImpl::info);
    }

    @Override
    @Transactional
    public ProductInfo proposeProduct(UUID workshopId, String code, String name, String brand, String reference) {
        var pc = ProductCode.of(code);
        var existing = products.findByWorkshopIdAndCode(workshopId, pc.value());
        if (existing.isPresent()) return info(existing.get());
        return info(products.save(Product.propose(UuidV7.next(), workshopId, pc, name, brand, reference, clock.instant())));
    }

    @Override
    @Transactional
    public SerialRegistration registerSerial(UUID workshopId, UUID motorcycleId, UUID orderId, String rawSerial, String reason) {
        var serial = SerialNumber.of(rawSerial);
        var now = clock.instant();
        var active = serials.findActive(serial.value());
        if (active.isPresent()) return resolve(active.get(), motorcycleId, workshopId, orderId, serial);

        UUID retiredId = jdbc.sql("SELECT id FROM catalog.serial WHERE normalized_serial = ? AND status = 'RETIRED' "
                        + "ORDER BY registered_at DESC LIMIT 1").param(serial.value()).query(UUID.class).optional().orElse(null);
        String cleanReason = retiredId == null ? null
                : Serial.requireReason(reason, "REACTIVATION_REASON_REQUIRED", "Ese serial fue retirado: indique el motivo para reactivarlo");
        // The activation runs behind a JDBC savepoint: the loser of a race hits the unique constraint, rolls back to
        // the savepoint and the surrounding order transaction keeps going (RN-94, section 15.6).
        boolean activated = withSavepoint(() -> {
            if (retiredId != null) {
                jdbc.sql("UPDATE catalog.serial SET status = 'ACTIVE', active_serial = ?, motorcycle_id = ?, workshop_id = ?, "
                                + "work_order_id = ?, registered_at = ?, retired_at = NULL, reason = ? WHERE id = ?")
                        .params(serial.value(), motorcycleId, workshopId, orderId, Timestamp.from(now), cleanReason, retiredId).update();
            } else {
                jdbc.sql("INSERT INTO catalog.serial(id, normalized_serial, serial_hash, motorcycle_id, workshop_id, work_order_id, "
                                + "status, active_serial, registered_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)")
                        .params(UuidV7.next(), serial.value(), serial.hash(), motorcycleId, workshopId, orderId,
                                serial.value(), Timestamp.from(now)).update();
            }
        });
        if (!activated) {
            var winner = serials.findActive(serial.value());
            if (winner.isPresent()) return resolve(winner.get(), motorcycleId, workshopId, orderId, serial);
            throw new IllegalStateException("serial activation failed without a winner");
        }
        var saved = serials.findActive(serial.value()).orElseThrow();
        return new SerialRegistration.Accepted(saved.getId(), serial.hash());
    }

    /** Runs the statement behind a savepoint on the transaction's own connection; false on a unique violation. */
    private boolean withSavepoint(Runnable statement) {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            var savepoint = connection.setSavepoint();
            try {
                statement.run();
                connection.releaseSavepoint(savepoint);
                return true;
            } catch (DataIntegrityViolationException e) {
                connection.rollback(savepoint);
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private SerialRegistration resolve(Serial active, UUID motorcycleId, UUID workshopId, UUID orderId, SerialNumber serial) {
        if (active.getMotorcycleId().equals(motorcycleId)) { // reinstalled on the same motorcycle: no alert
            return new SerialRegistration.Accepted(active.getId(), serial.hash());
        }
        events.publishEvent(new SerialRejected(workshopId, orderId, serial.hash(), clock.instant()));
        return new SerialRegistration.Rejected(serial.hash());
    }

    @Override
    @Transactional
    public void releaseForOrder(UUID orderId) {
        var now = clock.instant();
        serials.findByWorkOrderIdAndStatus(orderId, "ACTIVE").forEach(s -> s.retire("Orden cancelada", now));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveOn(UUID serialId, UUID motorcycleId) {
        return serials.findById(serialId).filter(Serial::isActive).map(s -> s.getMotorcycleId().equals(motorcycleId)).orElse(false);
    }

    static ProductInfo info(Product p) {
        return new ProductInfo(p.getId(), p.getCode(), p.getCodeType(), p.getName(), p.getBrand(), p.getReference(), p.getStatus());
    }
}
