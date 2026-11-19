package co.nitida.shared.tenancy;

import co.nitida.shared.WorkshopContext;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Second barrier of the workshop isolation (spec section 8.2): tells PostgreSQL which workshop the current
 * transaction acts for, so its row-level security policies hide every other workshop's rows.
 *
 * <p>It is set when a transaction begins, from the request's {@link WorkshopContext}, so no use case can forget it.
 * Code that runs without a request (event listeners, the scheduler) must say who it acts for with {@link #use} or
 * {@link #system}. {@code set_config(..., true)} is transaction-local: nothing leaks to the next user of the pooled
 * connection, and the workshop id travels as a bind parameter, never concatenated.
 */
@Component
public class TenantSession implements TransactionExecutionListener {
    private final DataSource dataSource;
    private final boolean enabled;

    TenantSession(DataSource dataSource, @Value("${nitida.rls.enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure == null) WorkshopContext.currentOrEmpty().ifPresent(ctx -> use(ctx.workshopId()));
    }

    /** The rest of this transaction sees only this workshop's rows. */
    public void use(UUID workshopId) {
        set("app.workshop_id", workshopId.toString());
    }

    /** For jobs that must look at every workshop (the alert scheduler). Explicit and per transaction, never a default. */
    public void system() {
        set("app.system", "on");
    }

    private void set(String key, String value) {
        if (!enabled) return; // H2 (default tests) has no row-level security
        var connection = DataSourceUtils.getConnection(dataSource); // the one bound to the running transaction
        try (var ps = connection.prepareStatement("select set_config(?, ?, true)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("could not set the workshop for row-level security", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
