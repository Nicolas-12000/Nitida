package co.nitida.files.application;

import co.nitida.files.domain.StoredFileRepository;
import co.nitida.files.infrastructure.FileStorage;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadFileUseCase {
    public record Command(WorkshopContext ctx, UUID fileId) {}

    public record Result(byte[] content, String mimeType) {}

    private final StoredFileRepository files;
    private final FileStorage storage;

    ReadFileUseCase(StoredFileRepository files, FileStorage storage) {
        this.files = files;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Result execute(Command c) {
        var file = files.findByIdAndWorkshopId(c.fileId(), c.ctx().workshopId()).orElseThrow(ApiException::notFound);
        return new Result(storage.load(file.getPath()), file.getMimeType());
    }
}
