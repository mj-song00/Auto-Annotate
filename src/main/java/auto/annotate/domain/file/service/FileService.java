package auto.annotate.domain.file.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    void save(MultipartFile multipartFile);
}
