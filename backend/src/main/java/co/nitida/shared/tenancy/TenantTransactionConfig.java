package co.nitida.shared.tenancy;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/** Every transaction of the application starts with its workshop selected (see {@link TenantSession}). */
@Configuration
class TenantTransactionConfig {
    TenantTransactionConfig(PlatformTransactionManager transactionManager, TenantSession tenantSession) {
        // Boot declares the bean as the interface, so it is matched by type here and checked: a different manager
        // would silently skip the second barrier, and that must never go unnoticed.
        if (!(transactionManager instanceof AbstractPlatformTransactionManager configurable)) {
            throw new IllegalStateException("Row-level security needs a transaction manager that accepts listeners, got "
                    + transactionManager.getClass().getName());
        }
        configurable.addListener(tenantSession);
    }
}
