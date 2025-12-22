package auto.annotate.domain.document.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.highlight.overlay.HighlightMark;
import auto.annotate.domain.highlight.overlay.HighlightMarkCollector;
import auto.annotate.domain.highlight.overlay.PdfOverlayRenderer;
import auto.annotate.domain.highlight.service.HighlightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
        String bundleKey = java.util.UUID.randomUUID().toString();
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
                    storedFilename,
                    bundleKey
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
            // PDF 파싱
            List<VisitSummaryRecord> parsedRecords = parsePdfToRecordsFromPdf(originalFilePath);
            log.info("확인2");
            try {
                List<VisitSummaryRecord> highlightedRecords =
                        highlightService.applyHighlights(parsedRecords, condition);
                log.info("확인3 - applyHighlights 끝, size={}", highlightedRecords.size());

                generateHighlightedPdf(highlightedRecords, originalFilePath, tempHighlightedFilePath);
                log.info("확인4 - generateHighlightedPdf 끝, output={}", tempHighlightedFilePath);

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
        } catch (Exception e) {
            log.error("🔥 condition={} 처리 중 예외", condition, e);
            throw e;
        }

    }


    private List<VisitSummaryRecord> parsePdfToRecordsFromPdf(Path pdfPath) {
        log.info("확인3");
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
        long t0 = System.currentTimeMillis();
        log.info("확인4-START generateHighlightedPdf: records={}, pdf={}",
                records == null ? 0 : records.size(), originalPdf.getFileName());

        if (records == null || records.isEmpty()) {
            try (PDDocument document = PDDocument.load(originalPdf.toFile())) {
                document.save(outputPdf.toFile());
            } catch (IOException e) {
                throw new RuntimeException("PDF 저장 실패(대상 없음)", e);
            }
            log.info("확인4-END generateHighlightedPdf: empty records, elapsedMs={}", System.currentTimeMillis() - t0);
            return;
        }

        try (PDDocument document = PDDocument.load(originalPdf.toFile())) {

            // ✅ 실제로 찍힌 결과 기준: overlay 데이터
            List<HighlightMark> marks = new ArrayList<>();
            EnumMap<HighlightType, Integer> summaryCounts = new EnumMap<>(HighlightType.class);

            // ✅ pageNumber 기준으로 레코드 그룹핑
            Map<Integer, List<VisitSummaryRecord>> byPage = new HashMap<>();

            for (VisitSummaryRecord r : records) {
                Integer raw = r.getPageNumber();
                if (raw == null) continue;

                // ⚠️ pageNumber가 1-based면 raw - 1
                int pageIndex = raw;

                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    log.warn("record pageNumber out of range. pageIndex={}, pages={}, record={}",
                            pageIndex, document.getNumberOfPages(), r);
                    continue;
                }
                byPage.computeIfAbsent(pageIndex, k -> new ArrayList<>()).add(r);
            }

            int highlightCount = 0;

            for (Map.Entry<Integer, List<VisitSummaryRecord>> entry : byPage.entrySet()) {
                int pageIndex = entry.getKey();
                PDPage page = document.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight();

                Map<String, List<PDRectangle>> areasCache = new HashMap<>();
                List<VisitSummaryRecord> pageRecords = entry.getValue();

                for (VisitSummaryRecord record : pageRecords) {
                    Set<HighlightType> types = record.getHighlightTypes();
                    if (types == null || types.isEmpty()) continue;

                    for (HighlightType type : types) {
                        String rawTarget = switch (type) {
                            case VISIT_OVER_7_DAYS -> record.getInstitutionName();
                            case HAS_HOSPITALIZATION, HAS_SURGERY, MONTH_30_DRUG -> record.getTreatmentDetail();
                        };
                        if (rawTarget == null) continue;

                        String targetText = rawTarget.trim();
                        if (targetText.isBlank()) continue;

                        String normalizedTarget = targetText.replaceAll("\\s+", "");
                        if (normalizedTarget.isBlank()) continue;

                        String cacheKey = type.name() + "|" + normalizedTarget;

                        List<PDRectangle> areas = areasCache.get(cacheKey);
                        if (areas == null) {
                            try {
                                areas = calculateTextPositions(document, pageIndex, targetText);
                            } catch (IOException e) {
                                throw new RuntimeException("텍스트 위치 계산 실패: pageIndex=" + pageIndex + ", text=" + targetText, e);
                            }
                            areasCache.put(cacheKey, areas);
                        }

                        if (areas == null || areas.isEmpty()) continue;

                        for (PDRectangle rect : areas) {
                            float x1 = rect.getLowerLeftX();
                            float y1 = pageHeight - rect.getUpperRightY();
                            float x2 = rect.getUpperRightX();
                            float y2 = pageHeight - rect.getLowerLeftY();

                            PDAnnotationTextMarkup highlight =
                                    new PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);

                            highlight.setConstantOpacity(0.3f);
                            highlight.setColor(type.getPDColor());

                            highlight.setQuadPoints(new float[]{
                                    x1, y2,
                                    x2, y2,
                                    x1, y1,
                                    x2, y1
                            });

                            PDRectangle bbox = new PDRectangle();
                            bbox.setLowerLeftX(x1);
                            bbox.setLowerLeftY(y1);
                            bbox.setUpperRightX(x2);
                            bbox.setUpperRightY(y2);

                            highlight.setRectangle(bbox);
                            page.getAnnotations().add(highlight);
                            highlightCount++;

                            // ✅ "이 PDF에서 실제로 하이라이트가 찍힌 것"만 집계/마크 추가
                            summaryCounts.put(type, summaryCounts.getOrDefault(type, 0) + 1);

                            // ✅ overlay 마진바/탭에 쓸 마크도 함께 만든다
                            marks.add(new HighlightMark(pageIndex, type, bbox));
                        }
                    }
                }
            }

            // ✅ 오버레이 그리기 (요약=실제 하이라이트 집계, 탭/마진=실제 하이라이트 marks)
            PdfOverlayRenderer renderer = new PdfOverlayRenderer(document);
            renderer.render(document, marks, summaryCounts);

            document.save(outputPdf.toFile());
            log.info("확인4-END generateHighlightedPdf: highlights={}, elapsedMs={}",
                    highlightCount, System.currentTimeMillis() - t0);

        } catch (IOException e) {
            throw new RuntimeException("PDF 하이라이트 생성 실패", e);
        }
    }


    /**
     * 실제 텍스트 위치 계산
     * PDDocument, pageIndex, 하이라이트할 텍스트를 받아 PDRectangle 리스트 반환
     */
    private List<PDRectangle> calculateTextPositions(
            PDDocument document,
            int pageIndex,
            String targetText
    ) throws IOException {

        List<TextPosition> positionsNoSpace = new ArrayList<>();
        StringBuilder normalizedPageText = new StringBuilder();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                for (TextPosition pos : textPositions) {
                    String ch = pos.getUnicode();
                    if (ch == null) continue;

                    // 공백/개행/탭 제거 (pageText와 positions 인덱스를 동일 기준으로 맞춤)
                    if (ch.isBlank()) continue;

                    normalizedPageText.append(ch);
                    positionsNoSpace.add(pos);
                }
            }
        };

        // ✅ 해당 페이지만 처리
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);

        String pageText = normalizedPageText.toString();
        String normalizedTarget = targetText.replaceAll("\\s+", "");

        List<PDRectangle> rectangles = new ArrayList<>();

        int index = pageText.indexOf(normalizedTarget);
        while (index >= 0) {
            int start = index;
            int end = index + normalizedTarget.length() - 1;

            if (start < 0 || end >= positionsNoSpace.size()) {
                break;
            }

            TextPosition startPos = positionsNoSpace.get(start);
            TextPosition endPos = positionsNoSpace.get(end);

            float x1 = startPos.getXDirAdj();
            float x2 = endPos.getXDirAdj() + endPos.getWidthDirAdj();

            float yTop = startPos.getYDirAdj();
            float height = startPos.getHeightDir();

            // ✅ "텍스트 좌표계" 그대로 반환
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

    private Map<HighlightType, Integer> countByTypeFromRecords(List<VisitSummaryRecord> records) {
        EnumMap<HighlightType, Integer> map = new EnumMap<>(HighlightType.class);
        if (records == null) return map;

        for (VisitSummaryRecord r : records) {
            Set<HighlightType> types = r.getHighlightTypes();
            if (types == null || types.isEmpty()) continue;

            for (HighlightType t : types) {
                map.put(t, map.getOrDefault(t, 0) + 1);
            }
        }
        return map;
    }
}