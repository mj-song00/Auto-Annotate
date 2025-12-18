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
import org.apache.pdfbox.text.TextPosition;
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
    public Resource loadHighlightedFileAsResource(UUID documentId, int condition) {

        log.info("시작");
        // 1. DB에서 Document 조회 및 원본 파일 경로 획득
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String storedFilename = document.getFileUrl();
        Path originalFilePath = Paths.get(uploadDir, document.getFileUrl());

        log.info("확인1");
        // 임시 하이라이팅 파일 경로 생성 (UUID를 사용하여 파일명 충돌 방지)
        String tempHighlightedFileName = "temp-" + UUID.randomUUID() + "-" +
                document.getFileUrl().replace(".pdf", "-highlighted.pdf");
        Path tempHighlightedFilePath = Paths.get(uploadDir, tempHighlightedFileName);

        try {
            // A. PDF 파싱
            List<VisitSummaryRecord> parsedRecords = parsePdfToRecordsFromPdf(originalFilePath);
            log.info("확인2");
            // B. 조건별 하이라이트 적용
            List<VisitSummaryRecord> highlightedRecords = highlightService.applyHighlights(parsedRecords, condition);

            // C. PDF 생성 (하이라이트 적용)
            generateHighlightedPdf(highlightedRecords, originalFilePath, tempHighlightedFilePath);


            // 2. 생성된 임시 파일을 Resource로 로드
            Resource resource = new UrlResource(tempHighlightedFilePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("Temporary highlighted PDF created and served: {}", tempHighlightedFilePath);
                return resource;
            } else {
                throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
            }
        } catch (MalformedURLException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        } finally {
            // 임시 파일 삭제는 운영 환경에서는 별도 스케줄링 필요
            log.warn("Temporary file deletion skipped for demonstration. Implement proper file cleanup.");
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

    /**
     * PDF 생성 + 조건별 하이라이트 적용
     */
    private void generateHighlightedPdf(
            List<VisitSummaryRecord> records,
            Path originalPdf,
            Path outputPdf
    ) {
        try (PDDocument document = PDDocument.load(originalPdf.toFile())) {

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight(); // ⭐ 중요

                for (VisitSummaryRecord record : records) {
                    for (HighlightType type : record.getHighlightTypes()) {

                        String targetText = switch (type) {
                            case VISIT_OVER_7_DAYS -> record.getInstitutionName();
                            case HAS_HOSPITALIZATION, HAS_SURGERY, MONTH_30_DRUG ->
                                    record.getTreatmentDetail();
                        };

                        if (targetText == null || targetText.isBlank()) continue;

                        List<PDRectangle> areas =
                                calculateTextPositions(document, pageIndex, targetText);

                        for (PDRectangle rect : areas) {

                            // ⭐ 좌표계 변환 (핵심)
                            float x1 = rect.getLowerLeftX();
                            float y1 = pageHeight - rect.getUpperRightY();
                            float x2 = rect.getUpperRightX();
                            float y2 = pageHeight - rect.getLowerLeftY();

                            PDAnnotationTextMarkup highlight =
                                    new PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);

                            highlight.setConstantOpacity(0.3f);
                            highlight.setColor(type.getPDColor());

                            // ⭐ QuadPoints (UL → UR → LL → LR)
                            highlight.setQuadPoints(new float[]{
                                    x1, y2,
                                    x2, y2,
                                    x1, y1,
                                    x2, y1
                            });

                            // ⭐ Bounding box
                            PDRectangle bbox = new PDRectangle();
                            bbox.setLowerLeftX(x1);
                            bbox.setLowerLeftY(y1);
                            bbox.setUpperRightX(x2);
                            bbox.setUpperRightY(y2);

                            highlight.setRectangle(bbox);
                            page.getAnnotations().add(highlight);
                        }
                    }
                }
            }

            document.save(outputPdf.toFile());
        } catch (IOException e) {
            throw new RuntimeException("PDF 하이라이트 생성 실패", e);
        }
    }

    /**
     * 실제 텍스트 위치 계산
     * PDDocument, PDPage, 하이라이트할 텍스트를 받아 PDRectangle 리스트 반환
     */
    private List<PDRectangle> calculateTextPositions(
            PDDocument document,
            int pageIndex,
            String targetText
    ) throws IOException {

        List<TextPosition> allPositions = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                for (TextPosition pos : textPositions) {
                    fullText.append(pos.getUnicode());
                    allPositions.add(pos);
                }
            }
        };

        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);

        String pageText = fullText.toString().replaceAll("\\s+", "");
        String normalizedTarget = targetText.replaceAll("\\s+", "");

        List<PDRectangle> rectangles = new ArrayList<>();

        int index = pageText.indexOf(normalizedTarget);
        while (index >= 0) {
            int start = index;
            int end = index + normalizedTarget.length() - 1;

            TextPosition startPos = allPositions.get(start);
            TextPosition endPos = allPositions.get(end);

            float x1 = startPos.getXDirAdj();
            float x2 = endPos.getXDirAdj() + endPos.getWidthDirAdj();

            float yTop = startPos.getYDirAdj();
            float height = startPos.getHeightDir();

            // ⭐ 여기서는 "텍스트 기준 좌표" 그대로 반환
            rectangles.add(new PDRectangle(
                    x1,
                    yTop - height,
                    x2 - x1,
                    height
            ));

            index = pageText.indexOf(normalizedTarget, index + 1);
        }

        return rectangles;
    }
}