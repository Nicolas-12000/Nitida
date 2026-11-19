package co.nitida.workshops.application;

import co.nitida.workshops.WorkshopsApi;
import java.time.Clock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gives the platform signing identity (migration V13) its key on the first start; a no-op afterwards. */
@Service
public class EnsurePlatformSignerUseCase {
    private final SigningKeys keys;
    private final Clock clock;

    EnsurePlatformSignerUseCase(SigningKeys keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void execute() {
        if (!keys.exists(WorkshopsApi.PLATFORM_ID)) keys.create(WorkshopsApi.PLATFORM_ID, clock.instant());
    }
}
