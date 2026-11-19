package co.nitida.files.application;

import co.nitida.files.FilesApi;
import co.nitida.files.domain.StoredFileRepository;
import co.nitida.shared.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FilesApiImpl implements FilesApi {
    private final StoredFileRepository files;

    FilesApiImpl(StoredFileRepository files) {
        this.files = files;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOwned(UUID fileId, UUID workshopId) {
        files.findByIdAndWorkshopId(fileId, workshopId).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public String sha256(UUID fileId, UUID workshopId) {
        return files.findByIdAndWorkshopId(fileId, workshopId).orElseThrow(ApiException::notFound).getSha256();
    }
}
