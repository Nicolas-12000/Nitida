package co.nitida.files.application;

import co.nitida.files.domain.ImageSanitizer;
import co.nitida.files.domain.StoredFile;
import co.nitida.files.domain.StoredFileRepository;
import co.nitida.files.infrastructure.FileStorage;
import co.nitida.shared.UuidV7;
import co.nitida.shared.WorkshopContext;
import co.nitida.shared.crypto.Sha256;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadFileUseCase {
    public record Command(WorkshopContext ctx, byte[] content) {}

    public record Result(UUID fileId, String sha256, long sizeBytes) {}

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final Clock clock;

    UploadFileUseCase(StoredFileRepository files, FileStorage storage, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
    }

    /** Any member of the workshop may upload (mechanics photograph, front desk photographs the dashboard). */
    @Transactional
    public Result execute(Command c) {
        byte[] clean = ImageSanitizer.sanitize(c.content());
        UUID id = UuidV7.next();
        String path = storage.save(id, clean);
        String hash = Sha256.hex(clean);
        files.save(new StoredFile(id, c.ctx().workshopId(), "image/jpeg", clean.length, hash, path, clock.instant()));
        return new Result(id, hash, clean.length);
    }
}
