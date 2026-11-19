package co.nitida.catalog.domain;

import java.util.UUID;

/** P10 / RN-94: an active serial belongs to at most one motorcycle. The same motorcycle may reinstall it. */
public final class ActiveSerials {
    private ActiveSerials() {}

    public static boolean claim(UUID currentHolder, UUID motorcycleId) {
        return currentHolder == null || currentHolder.equals(motorcycleId);
    }
}
