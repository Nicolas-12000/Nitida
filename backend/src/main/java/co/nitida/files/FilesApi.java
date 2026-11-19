package co.nitida.files;

import java.util.UUID;

/** Public API of the files module. */
public interface FilesApi {
    /** The file must exist and belong to the workshop; otherwise 404 (never reveals other workshops' files). */
    void requireOwned(UUID fileId, UUID workshopId);

    /** SHA-256 (hex) of the stored, re-encoded file: goes into the private content of the history. */
    String sha256(UUID fileId, UUID workshopId);
}
