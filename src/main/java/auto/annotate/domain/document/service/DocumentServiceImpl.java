package auto.annotate.domain.document.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.PdfRowRecord;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.highlight.overlay.HighlightMark;
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
import org.springframework.core.io.FileSystemResource;
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
                throw new BaseException(ExceptionEnum.FILE_SAVE_FAILED);
            }

            HighlightTarget target = detectHighlightTargetFromFile(targetLocation);

            Document document = new Document(
                    originalFilename,
                    storedFilename,
                    bundleKey,
                    target
            );

            Document savedDocument = documentRepository.save(document);
            savedDocuments.add(savedDocument);
        }
        return savedDocuments;
    }



    /**
     * GET /document/{id}/highlighted
     * 사용자가 요청할 때 하이라이트 PDF를 생성(캐시)하고 Resource로 반환
     */
    @Override
    public Resource loadHighlightedFileAsResource(UUID documentId, int condition) {

        log.info("🔥 highlighted 요청 documentId={}, condition={}", documentId, condition);

        // 1) 기준 document로 bundleKey 확보
        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String bundleKey = base.getBundleKey();
        if (bundleKey == null || bundleKey.isBlank()) {
            throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND); // 적당히 바꿔도 됨
        }

        // 2) condition -> HighlightType
        HighlightType onlyType = mapConditionToType(condition);

        // 3) HighlightType이 요구하는 target PDF 선택
        HighlightTarget targetToRender = onlyType.getTarget();

        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, targetToRender)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        // 4) 원본 PDF 경로
        Path originalPdfPath = Paths.get(uploadDir, targetDoc.getFileUrl());

        if (!Files.exists(originalPdfPath)) {
            throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);
        }

        // 5) parse -> applyHighlights -> generate
        List<PdfRowRecord> rows = parsePdfToRows(originalPdfPath, targetToRender);
        List<PdfRowRecord> highlightedRecords = highlightService.applyHighlights(rows, condition);

        long marked = highlightedRecords.stream()
                .filter(r -> r.getHighlightTypes() != null && !r.getHighlightTypes().isEmpty())
                .count();
        log.info("before generate: bundleKey={}, targetToRender={}, condition={}, type={}, markedRows={}",
                bundleKey, targetToRender, condition, onlyType, marked);

        Path out = resolveHighlightedOutputPath(bundleKey, targetToRender, condition);
        generateHighlightedPdf(highlightedRecords, originalPdfPath, out);

        return new FileSystemResource(out);
    }

    @Override
    public Resource loadHighlightedByBundle(String bundleKey, int condition) {
        HighlightType type = mapConditionToType(condition);
        HighlightTarget needed = type.getTarget();

        Document doc = documentRepository.findFirstByBundleKeyAndTarget(bundleKey, needed)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        return loadHighlightedFileAsResource(doc.getId(), condition);
    }

    /**
     * (선택) highlightService가 전체 타입을 세팅해주는 방식이라면 condition별로만 남기고 싶을 때 사용
     * - VisitSummaryRecord.getHighlightTypes()가 "mutable set"일 때만 안전함
     */
    private List<VisitSummaryRecord> filterByCondition(
            List<VisitSummaryRecord> original,
            List<VisitSummaryRecord> applied,
            int condition
    ) {
        HighlightType only = mapConditionToType(condition);
        if (only == null) return applied;

        for (VisitSummaryRecord r : applied) {
            Set<HighlightType> types = r.getHighlightTypes();
            if (types == null) continue;
            types.retainAll(Collections.singleton(only));
        }
        return applied;
    }

    private HighlightType mapConditionToType(int condition) {
        // 너의 condition 매핑 규칙에 맞게 수정
        return switch (condition) {
            case 0 -> HighlightType.VISIT_OVER_7_DAYS;
            case 1 -> HighlightType.MONTH_30_DRUG;
            case 2 -> HighlightType.HAS_HOSPITALIZATION;
            case 3 -> HighlightType.HAS_SURGERY;
            default -> null;
        };
    }

    /**
     * ✅ 페이지별로 텍스트를 뽑아서 record에 pageNumber를 넣어준다.
     * (지금 generateHighlightedPdf가 pageNumber 기반으로 하이라이트를 찍기 때문)
     */
    private static final java.util.regex.Pattern ROW_PATTERN =
            java.util.regex.Pattern.compile(
                    // (선택) 순번
                    "^(?:\\s*(\\d+)\\s+)?" +
                            // 병원명 (공백 포함)
                            "(.+?)\\s+" +
                            // 입원(외래)일수: 11(0) or 11
                            "(\\d+(?:\\(\\d+\\))?)\\s+" +
                            // 금액 3개(콤마 포함)
                            "([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)" +
                            // (선택) 뒤에 남는 텍스트
                            "(?:\\s+(.*))?$"
            );

    /**
     * ✅ PDF(docType=HighlightTarget)별로 "행(row)"을 복원(줄바꿈 합치기)한 뒤 VisitSummaryRecord로 파싱한다.
     * - HighlightTarget은 이미 프로젝트에서 사용중인 enum을 그대로 재사용한다.
     * - pageNumber는 0-based(pageIndex)로 넣는다. (generateHighlightedPdf가 0-based로 사용 중)
     */
    private static final java.util.regex.Pattern ROW_START_SEQ =
            java.util.regex.Pattern.compile("^\\d+\\s+.*"); // "1 ..."

    private static final java.util.regex.Pattern ROW_START_SEQ_DATE =
            java.util.regex.Pattern.compile("^\\d+\\s+\\d{4}-\\d{2}-\\d{2}\\s+.*"); // "1 2025-04-29 ..."

    private List<PdfRowRecord> parsePdfToRecordsFromPdf(Path pdfPath) {
        log.info("📄 parsePdfToRecordsFromPdf: {}", pdfPath.getFileName());

        List<PdfRowRecord> out = new ArrayList<>();

        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            // 1) 이 PDF가 어떤 타입인지(=어떤 파서를 쓸지) 감지
            HighlightTarget target = detectHighlightTarget(doc, stripper);
            log.info("📌 detected target={}", target);

            // 2) 줄바꿈으로 쪼개진 한 행(row)을 다시 합치기 위한 버퍼
            StringBuilder rowBuf = new StringBuilder();

            int pages = doc.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);

                String text = stripper.getText(doc);
                String[] lines = text.split("\\r?\\n");

                for (String rawLine : lines) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (line.isEmpty()) continue;

                    // 헤더/설명 줄 제거 (필요하면 더 추가)
                    if (isHeaderOrNoiseLine(line)) continue;

                    boolean newRow = isRowStart(target, line);

                    if (newRow) {
                        flushRow(out, target, rowBuf, pageIndex);
                        rowBuf.append(line);
                    } else {
                        // 같은 행의 줄바꿈 조각이면 이어붙임
                        if (!rowBuf.isEmpty()) rowBuf.append(" ");
                        rowBuf.append(line);
                    }
                }
            }

            // 마지막 버퍼 flush
            flushRow(out, target, rowBuf, Math.max(0, pages - 1));
            return out;

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * ✅ PDF 첫 페이지 텍스트로 HighlightTarget 판별
     * - 네가 올린 4종 PDF 제목 문자열을 기준으로 분기
     */
    private HighlightTarget detectHighlightTarget(PDDocument doc, PDFTextStripper stripper) throws IOException {
        stripper.setStartPage(1);
        stripper.setEndPage(1);
        String firstPage = stripper.getText(doc);

        if (firstPage.contains("진료정보요약")) return HighlightTarget.VISIT_SUMMARY;
        if (firstPage.contains("기본진료정보")) return HighlightTarget.DRUG_SUMMARY; // "BASIC"이 없으니 임시 매핑
        if (firstPage.contains("세부진료정보")) return HighlightTarget.TREATMENT_DETAIL;
        if (firstPage.contains("처방조제정보")) return HighlightTarget.PRESCRIPTION;

        // fallback(원하는 정책으로 변경 가능)
        return HighlightTarget.VISIT_SUMMARY;
    }

    /**
     * ✅ target별 "새 행 시작" 규칙
     * - 진료정보요약: "순번(숫자) + ..." 형태
     * - 나머지: "순번 + 날짜 + ..." 형태
     */
    private boolean isRowStart(HighlightTarget target, String line) {
        return switch (target) {
            case VISIT_SUMMARY -> ROW_START_SEQ.matcher(line).find();
            case DRUG_SUMMARY, TREATMENT_DETAIL, PRESCRIPTION -> ROW_START_SEQ_DATE.matcher(line).find();
        };
    }

    private boolean isHeaderOrNoiseLine(String line) {
        // 공통 헤더/설명 제거
        if (line.startsWith("순번")) return true;

        // 진료정보요약 표 헤더들
        if (line.contains("병·의원&약국")) return true;
        if (line.contains("입원(외래)일수")) return true;
        if (line.contains("총 진료비")) return true;
        if (line.contains("혜택받은 금액")) return true;
        if (line.contains("내가 낸 의료비")) return true;
        if (line.contains("(건강보험 적용분)")) return true;
        if (line.contains("(진료비)")) return true;

        // 기본/세부/처방 표 헤더들
        if (line.contains("진료시작일")) return true;
        if (line.contains("주상병")) return true;
        if (line.contains("코드")) return true;
        if (line.contains("진료내역")) return true;
        if (line.contains("약품명")) return true;
        if (line.contains("성분명")) return true;
        if (line.contains("1회")) return true;
        if (line.contains("투약량")) return true;
        if (line.contains("투여횟수")) return true;
        if (line.contains("총")) return true; // "총 투약일수" 등

        // 섹션 제목 자체
        if (line.contains("진료정보요약")) return true;
        if (line.contains("기본진료정보")) return true;
        if (line.contains("세부진료정보")) return true;
        if (line.contains("처방조제정보")) return true;

        return false;
    }

    private void flushRow(List<PdfRowRecord> out, HighlightTarget target, StringBuilder rowBuf, int pageIndex) {
        if (rowBuf == null || rowBuf.isEmpty()) return;

        String row = rowBuf.toString().trim();
        rowBuf.setLength(0);

        PdfRowRecord parsed = parseRowByTarget(target, row, pageIndex);
        if (parsed != null) out.add(parsed);
    }

    /**
     * ✅ target별 row 파싱
     * - VISIT_SUMMARY: 진료정보요약(금액 3개 포함) 정규식 파싱
     * - DRUG_SUMMARY(=기본진료정보): MVP로 뒤에서 금액 3개 + 내원일수만 뽑고 나머지는 원문 유지
     * - TREATMENT_DETAIL / PRESCRIPTION: MVP로 맨 끝 "총투약일수"만 daysOfStayOrVisit에 넣고 원문 유지
     */
    private PdfRowRecord parseRowByTarget(HighlightTarget target, String row, int pageIndex) {
        return switch (target) {
            case VISIT_SUMMARY -> parseVisitSummaryRow(row, pageIndex);
            case DRUG_SUMMARY -> parseBasicRowAsDrugSummary(row, pageIndex); // 기본진료정보 PDF
            case TREATMENT_DETAIL -> parseDetailRow(row, pageIndex);         // 세부진료정보 PDF
            case PRESCRIPTION -> parsePrescriptionRow(row, pageIndex);       // 처방조제정보 PDF
        };
    }

// --- Row parsers (MVP) ---

    private static final java.util.regex.Pattern VISIT_SUMMARY_ROW =
            java.util.regex.Pattern.compile("^\\s*(\\d+)\\s+(.+?)\\s+(\\d+(?:\\(\\d+\\))?)\\s+([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)\\s*$");

    private PdfRowRecord parseVisitSummaryRow(String row, int pageIndex) {
        var m = VISIT_SUMMARY_ROW.matcher(row);
        if (!m.find()) return null;

        String institutionName = m.group(2).trim();
        String days = m.group(3).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.VISIT_SUMMARY)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .daysOfStayOrVisit(days)
                .treatmentDetail(row) // 원문 전체(MVP)
                .build();
    }

    /**
     * 기본진료정보(MVP)
     * - row: "1 2025-04-29 <기관명...> 외래 AF900 ... <내원일수> <총진료비> <혜택> <본인부담>"
     */
    private PdfRowRecord parseBasicRowAsDrugSummary(String row, int pageIndex) {
        List<String> tokens = Arrays.asList(row.trim().split("\\s+"));
        if (tokens.size() < 8) return null;

        int n = tokens.size();
        String visitDays = tokens.get(n - 4);

        // 기관명: 날짜 이후 ~ "외래/입원" 직전(없으면 내원일수 직전)
        int start = 2; // seq(0), date(1) 다음
        int inOutIdx = indexOfAny(tokens, "외래", "입원");
        int endExclusive = (inOutIdx > start) ? inOutIdx : (n - 4);

        String institutionName = join(tokens, start, endExclusive).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.DRUG_SUMMARY)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .daysOfStayOrVisit(visitDays)   // 기본진료정보에서는 내원일수
                .treatmentDetail(row)           // MVP: 원문 유지
                .build();
    }

    /**
     * 세부진료정보(MVP)
     * - 맨 끝 토큰을 총투약일수로 간주
     */
    private PdfRowRecord parseDetailRow(String row, int pageIndex) {
        List<String> tokens = Arrays.asList(row.trim().split("\\s+"));
        if (tokens.size() < 6) return null;

        // String seq = tokens.get(0); // 내부 모델에 sequence 없으면 저장 안 함
        String totalDays = tokens.get(tokens.size() - 1); // 총투약일수

        int start = 2; // seq, date 다음
        int endExclusive = Math.max(start, tokens.size() - 3); // 마지막 3개(투약량/횟수/일수) 앞까지
        String institutionName = join(tokens, start, endExclusive).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.TREATMENT_DETAIL)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .daysOfStayOrVisit(totalDays)  // 총투약일수
                .treatmentDetail(row)          // MVP: 원문 유지
                .build();
    }

    /**
     * 처방조제정보(MVP)
     * - 맨 끝 토큰을 총투약일수로 간주
     */
    private PdfRowRecord parsePrescriptionRow(String row, int pageIndex) {
        List<String> tokens = Arrays.asList(row.trim().split("\\s+"));
        if (tokens.size() < 6) return null;

        // seq = tokens.get(0); // 내부 모델에 sequence 없으면 굳이 보관 안 함
        String totalDays = tokens.get(tokens.size() - 1);

        int start = 2; // seq, date 다음
        int endExclusive = Math.max(start, tokens.size() - 3); // 뒤쪽(예: 금액/일수 등) 제외
        String institutionName = join(tokens, start, endExclusive).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.PRESCRIPTION)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .daysOfStayOrVisit(totalDays)
                .treatmentDetail(row) // MVP: 원문 유지
                .build();
    }

// --- helpers ---

    private int indexOfAny(List<String> tokens, String... keys) {
        for (int i = 0; i < tokens.size(); i++) {
            for (String k : keys) {
                if (tokens.get(i).equals(k)) return i;
            }
        }
        return -1;
    }

    private String join(List<String> tokens, int fromInclusive, int toExclusive) {
        if (fromInclusive < 0) fromInclusive = 0;
        if (toExclusive > tokens.size()) toExclusive = tokens.size();
        if (fromInclusive >= toExclusive) return "";
        return String.join(" ", tokens.subList(fromInclusive, toExclusive));
    }
    /**
     * PDF 생성 + 조건별 하이라이트 적용
     */
    private void generateHighlightedPdf(
            List<PdfRowRecord> records,
            Path originalPdf,
            Path outputPdf
    ) {
        long t0 = System.currentTimeMillis();
        log.info("✅ generateHighlightedPdf START: records={}, pdf={}",
                records == null ? 0 : records.size(), originalPdf.getFileName());

        // 대상 없으면 그대로 복사 저장
        if (records == null || records.isEmpty()) {
            try (PDDocument document = PDDocument.load(originalPdf.toFile())) {
                document.save(outputPdf.toFile());
            } catch (IOException e) {
                throw new RuntimeException("PDF 저장 실패(대상 없음)", e);
            }
            log.info("✅ generateHighlightedPdf END(empty): elapsedMs={}", System.currentTimeMillis() - t0);
            return;
        }

        try (PDDocument document = PDDocument.load(originalPdf.toFile())) {

            List<HighlightMark> marks = new ArrayList<>();
            EnumMap<HighlightType, Integer> summaryCounts = new EnumMap<>(HighlightType.class);

            Map<Integer, List<PdfRowRecord>> byPage = new HashMap<>();
            for (PdfRowRecord r : records) {
                Integer pageIndex = r.getPageIndex();
                if (pageIndex == null) continue;

                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    log.warn("record pageNumber out of range. pageIndex={}, pages={}, record={}",
                            pageIndex, document.getNumberOfPages(), r);
                    continue;
                }
                byPage.computeIfAbsent(pageIndex, k -> new ArrayList<>()).add(r);
            }

            int highlightCount = 0;

            for (Map.Entry<Integer, List<PdfRowRecord>> entry : byPage.entrySet()) {
                int pageIndex = entry.getKey();
                PDPage page = document.getPage(pageIndex);
                float pageHeight = page.getMediaBox().getHeight();

                Map<String, List<PDRectangle>> areasCache = new HashMap<>();
                List<PdfRowRecord> pageRecords = entry.getValue();

                for (PdfRowRecord record : pageRecords) {
                    Set<HighlightType> types = record.getHighlightTypes();
                    if (types == null || types.isEmpty()) continue;

                    for (HighlightType type : types) {
                        if (type == HighlightType.HAS_HOSPITALIZATION) {
                            log.info("HOSP DEBUG pageIndex={}, days='{}', inst='{}', detail='{}'",
                                    pageIndex,
                                    record.getDaysOfStayOrVisit(),
                                    record.getInstitutionName(),
                                    record.getTreatmentDetail()
                            );
                        }

                        String rawTarget = switch (type) {
                            case VISIT_OVER_7_DAYS -> record.getInstitutionName();

                            //  입원은 진료정보요약의 "입원(외래)일수" 텍스트(예: 11(0))를 하이라이트
                            case HAS_HOSPITALIZATION -> record.getDaysOfStayOrVisit();

                            // TODO: 수술/30일약은 지금처럼 treatmentDetail을 쓰든, 해당 문서 타입에 맞게 바꿔야 함
                            case HAS_SURGERY, MONTH_30_DRUG -> record.getTreatmentDetail();
                        };
                        if (rawTarget == null) continue;

                        String targetText = rawTarget.trim();
                        if (targetText.isBlank()) continue;

                        String normalizedTarget = targetText.replaceAll("\\s+", "");
                        if (normalizedTarget.isBlank()) continue;

                        String cacheKey = type.name() + "|" + normalizedTarget;

                        List<PDRectangle> areas = areasCache.get(cacheKey);
                        if (areas == null) {
                            areas = calculateTextPositions(document, pageIndex, targetText);
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

                            summaryCounts.put(type, summaryCounts.getOrDefault(type, 0) + 1);
                            marks.add(new HighlightMark(pageIndex, type, bbox));
                        }
                    }
                }
            }

            PdfOverlayRenderer renderer = new PdfOverlayRenderer(document);
            renderer.render(document, marks, summaryCounts);

            document.save(outputPdf.toFile());
            log.info("✅ generateHighlightedPdf END: highlights={}, elapsedMs={}",
                    highlightCount, System.currentTimeMillis() - t0);

        } catch (IOException e) {
            throw new RuntimeException("PDF 하이라이트 생성 실패", e);
        }
    }

    /**
     * 실제 텍스트 위치 계산
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
                    if (ch.isBlank()) continue;

                    normalizedPageText.append(ch);
                    positionsNoSpace.add(pos);
                }
            }
        };

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

            if (start < 0 || end >= positionsNoSpace.size()) break;

            TextPosition startPos = positionsNoSpace.get(start);
            TextPosition endPos = positionsNoSpace.get(end);

            float x1 = startPos.getXDirAdj();
            float x2 = endPos.getXDirAdj() + endPos.getWidthDirAdj();

            float yTop = startPos.getYDirAdj();
            float height = startPos.getHeightDir();

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


    private HighlightTarget detectHighlightTargetFromFile(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String firstPage = stripper.getText(doc);

            if (firstPage.contains("진료정보요약")) return HighlightTarget.VISIT_SUMMARY;
            if (firstPage.contains("기본진료정보")) return HighlightTarget.DRUG_SUMMARY;      // 현재 enum 재사용
            if (firstPage.contains("세부진료정보")) return HighlightTarget.TREATMENT_DETAIL;
            if (firstPage.contains("처방조제정보")) return HighlightTarget.PRESCRIPTION;

            return HighlightTarget.VISIT_SUMMARY; // fallback 정책
        } catch (Exception e) {
            log.warn("detectHighlightTargetFromFile failed: {}", pdfPath.getFileName(), e);
            return HighlightTarget.VISIT_SUMMARY;
        }
    }

    private List<PdfRowRecord> parsePdfToRows(Path pdfPath, HighlightTarget target) {
        List<PdfRowRecord> rows = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            int pageCount = document.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);

                String pageText = stripper.getText(document);
                String[] lines = pageText.split("\\r?\\n");

                for (String line : lines) {
                    String row = line.trim();
                    if (row.isEmpty()) continue;

                    // ✅ 너가 이미 만든 target별 파서 사용
                    PdfRowRecord parsed = parseRowByTarget(target, row, pageIndex);
                    if (parsed != null) rows.add(parsed);
                }
            }

            return rows;

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        }
    }

    private Path resolveHighlightedOutputPath(String bundleKey, HighlightTarget target, int condition) {
        // 원하는 위치로 바꿔도 됨: uploadDir 아래 highlighted 폴더
        Path dir = Paths.get(uploadDir, "highlighted");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }

        String safeBundleKey = bundleKey.replaceAll("[^a-zA-Z0-9\\-]", "");
        String fileName = String.format("%s-%s-cond%d-highlighted.pdf", safeBundleKey, target.name(), condition);

        return dir.resolve(fileName);
    }
}