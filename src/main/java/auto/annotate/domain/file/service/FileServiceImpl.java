package auto.annotate.domain.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final FileRepository fileRepository;

    @Value("${pdf.file.upload-dir}")
    private String uploadDir;

    @Override
    public void save(MultipartFile multipartFile) throws IOException {
        // 1. 파일 시스템 저장 경로 준비 및 고유 식별자 (ID) 결정
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        UUID id = UUID.randomUUID();
        String originalFilename = multipartFile.getOriginalFilename();
        String storedFilename = id.toString() + ".pdf";

        Path targetLocation = uploadPath.resolve(storedFilename);

        // 2. 디스크에 파일 저장
        Files.copy(multipartFile.getInputStream(), targetLocation);

        // 3. Document 엔티티 생성 및 DB에 저장
        // ⭐⭐⭐ Setter 대신 Builder 패턴을 사용하여 객체 생성 시점에 모든 데이터 주입 ⭐⭐⭐
        Document document = Document.builder()
                .id(id)
                .title(originalFilename)
                .documentUrl(targetLocation.toString())
                .fileSize(multipartFile.getSize())
                .mimeType(multipartFile.getContentType())
                // .folderId(폴더 ID) // 실제 구현 시 필요
                .build();

        // JPA Repository를 사용하여 DB에 메타데이터 저장
        return fileRepository.save(document);
    }
}
