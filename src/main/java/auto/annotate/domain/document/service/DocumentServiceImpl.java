package auto.annotate.domain.document.service;

import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;

    @Value("${pdf.file.upload-dir}")
    private String uploadDir;

    @Override
    public List<Document> save(List<MultipartFile> multipartFiles) {

        List<Document> savedDocuments = new ArrayList<>();
        // 1. 파일 시스템 저장 경로 준비 및 고유 식별자 (ID) 결정
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath);
            } catch (IOException e) {
                // 디렉터리 생성 실패 시 처리 (옵션)
                throw new RuntimeException("Could not create upload directory!", e);
            }
        }

        for (MultipartFile multipartFile : multipartFiles) {
            // 파일이 비어있는 경우(null이거나 크기가 0) 건너뜁니다.
            if (multipartFile == null || multipartFile.isEmpty()) {
                continue;
            }

            UUID id = UUID.randomUUID();
            String originalFilename = multipartFile.getOriginalFilename();
            String storedFilename = id.toString() + ".pdf";

            Path targetLocation = uploadPath.resolve(storedFilename);

            // 2. 디스크에 파일 저장
            try {
                Files.copy(multipartFile.getInputStream(), targetLocation);
            } catch (IOException e) {
                // 파일 저장 실패 시, RuntimeException으로 변환하여 던집니다.
                throw new RuntimeException("Could not store file " + originalFilename + ". Please try again!", e);
            }

            Document document = new Document(
                    originalFilename,
                    storedFilename
            );

            Document savedDocument = documentRepository.save(document);
            savedDocuments.add(savedDocument);
        }
        return savedDocuments;
    }
}