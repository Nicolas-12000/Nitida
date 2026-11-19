package co.nitida.files.web;

import co.nitida.files.application.ReadFileUseCase;
import co.nitida.files.application.UploadFileUseCase;
import co.nitida.files.domain.ImageSanitizer;
import co.nitida.shared.ApiException;
import co.nitida.shared.WorkshopContext;
import jakarta.servlet.http.Part;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
class FileHandler {
    private final UploadFileUseCase upload;
    private final ReadFileUseCase read;

    FileHandler(UploadFileUseCase upload, ReadFileUseCase read) {
        this.upload = upload;
        this.read = read;
    }

    ServerResponse upload(ServerRequest req) throws Exception {
        Part part = req.multipartData().getFirst("file");
        if (part == null) throw ApiException.badRequest("FILE_REQUIRED", "Adjunte la foto en el campo 'file'");
        byte[] bytes;
        try (var in = part.getInputStream()) {
            bytes = in.readNBytes(ImageSanitizer.MAX_BYTES + 1); // never buffer more than the limit
        }
        var result = upload.execute(new UploadFileUseCase.Command(WorkshopContext.current(), bytes));
        return ServerResponse.status(HttpStatus.CREATED).body(result);
    }

    ServerResponse download(ServerRequest req) {
        var result = read.execute(new ReadFileUseCase.Command(WorkshopContext.current(), UUID.fromString(req.pathVariable("id"))));
        return ServerResponse.ok()
                .contentType(MediaType.parseMediaType(result.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(result.content());
    }
}
