package co.nitida.files.infrastructure;

import java.util.UUID;

/** Boundary to wherever bytes live. MVP: local volume; swap for object storage without touching the domain. */
public interface FileStorage {
    /** Stores the bytes and returns the relative path (the UUID: no user input ever reaches the disk path). */
    String save(UUID id, byte[] content);

    byte[] load(String path);
}
