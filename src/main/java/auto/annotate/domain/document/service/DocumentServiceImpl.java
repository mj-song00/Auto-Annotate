package auto.annotate.domain.document.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.highlight.service.HighlightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.MalformedURLException;
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
    private final HighlightService highlightService;


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


    /**
     * GET /document/{id}/highlighted: 사용자가 요청할 때 실시간으로 하이라이팅된 PDF를 생성하고 반환합니다.
     */
    @Override
    public Resource loadHighlightedFileAsResource(UUID documentId) {

        // 1. DB에서 Document 조회 및 원본 파일 경로 획득
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path originalFilePath = uploadPath.resolve(document.getFileUrl()).normalize();

        // 임시 하이라이팅 파일 경로 생성 (UUID를 사용하여 파일명 충돌 방지)
        String tempHighlightedFileName = "temp-" + UUID.randomUUID() + "-" + document.getFileUrl().replace(".pdf", "-highlighted.pdf");
        Path tempHighlightedFilePath = uploadPath.resolve(tempHighlightedFileName);

        try {
            // A. PDF 파싱
            List<VisitSummaryRecord> parsedRecords = parsePdfToRecordsFromPdf(originalFilePath);

            // B. 조건 검사
            List<VisitSummaryRecord> highlightedRecords = highlightService.applyHighlights(parsedRecords);
            // C. PDF 생성
            generateHighlightedPdf(highlightedRecords, originalFilePath, tempHighlightedFilePath);


            // 2. 생성된 임시 파일을 Resource로 로드
            Resource resource = new UrlResource(tempHighlightedFilePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // 임시 파일은 반환 후 삭제할 수 있도록 별도의 스케줄링 또는 후처리 로직 필요
                log.info("Temporary highlighted PDF created and served: {}", tempHighlightedFilePath);
                return resource;
            } else {
                throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
            }
        } catch (MalformedURLException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        } finally {
            // *** 주의: 실제 운영 환경에서는 임시 파일 삭제를 비동기 또는 AOP로 처리해야 합니다. ***
            // 현재는 간단한 구현을 위해 즉시 삭제 시도 (스트리밍 완료 후 삭제 보장 안 됨)
            try {
                // Files.deleteIfExists(tempHighlightedFilePath);
                log.warn("Temporary file deletion skipped for demonstration. Implement proper file cleanup.");
            } catch (Exception e) {
                log.error("Failed to delete temporary file: {}", tempHighlightedFilePath, e);
            }
        }
    }

    private List<VisitSummaryRecord> parsePdfToRecordsFromPdf(Path pdfPath) {
        List<VisitSummaryRecord> records = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // PDF 내용을 줄 단위로 나누기
            String[] lines = text.split("\\r?\\n");

            for (String line : lines) {
                // PDF 구조에 따라 공백/탭으로 컬럼 구분
                String[] columns = line.split("\\s+");
                if (columns.length >= 3) {
                    String institutionName = columns[0];
                    String daysOfStayOrVisit = columns[1];
                    String treatmentDetail = columns[2];

                    VisitSummaryRecord record = VisitSummaryRecord.builder()
                            .sequence(UUID.randomUUID().toString()) // UUID 사용
                            .institutionName(institutionName)
                            .daysOfStayOrVisit(daysOfStayOrVisit)
                            .treatmentDetail(treatmentDetail)
                            .totalMedicalFee("")        // PDF에 없으면 빈 문자열
                            .insuranceBenefit("")
                            .userPaidAmount("")
                            .build();

                    records.add(record); // 리스트에 추가
                }
            }

            return records;

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        }
    }

    private void generateHighlightedPdf(
            List<VisitSummaryRecord> records,
            Path originalPdf,
            Path outputPdf
    ) {
        try (PDDocument document = PDDocument.load(originalPdf.toFile())) {

            // PDF 전체 텍스트 가져오기 (읽기 전용)
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            String fullText = stripper.getText(document);

            // 페이지 단위로 하이라이트 적용
            for (PDPage page : document.getPages()) {

                // 페이지에 쓰기용 스트림 열기
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true)) {

                    for (VisitSummaryRecord record : records) {
                        for (HighlightType type : record.getHighlightTypes()) {
                            // 단순 예시: 텍스트 매칭 시 하이라이트
                            String targetText = switch (type) {
                                case VISIT_OVER_7_DAYS -> record.getInstitutionName();
                                case HAS_HOSPITALIZATION, HAS_SURGERY, MONTH_30_DRUG -> record.getTreatmentDetail();
                            };

                            // 텍스트 위치 계산 없이 전체 페이지 영역에 하이라이트
                            PDAnnotationTextMarkup highlight = new PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);
                            highlight.setConstantOpacity(0.3f);
                            highlight.setColor(new PDColor(new float[]{1, 1, 0}, PDDeviceRGB.INSTANCE)); // 노란색

                            PDRectangle rect = page.getMediaBox();
                            highlight.setRectangle(rect);
                            highlight.setQuadPoints(new float[]{
                                    rect.getLowerLeftX(), rect.getUpperRightY(),
                                    rect.getUpperRightX(), rect.getUpperRightY(),
                                    rect.getLowerLeftX(), rect.getLowerLeftY(),
                                    rect.getUpperRightX(), rect.getLowerLeftY()
                            });

                            page.getAnnotations().add(highlight);
                        }
                    }
                } // contentStream 자동 close
            }

            document.save(outputPdf.toFile());

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }
    }
}