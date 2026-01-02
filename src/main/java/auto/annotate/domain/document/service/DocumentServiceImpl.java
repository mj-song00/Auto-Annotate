package auto.annotate.domain.document.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.utils.SurgeryTokenMatcher;
import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.PdfRowRecord;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static auto.annotate.common.utils.HospitalKeyUtils.*;


@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;
    private final HighlightService highlightService;
    private final SurgeryTokenMatcher surgeryTokenMatcher;

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

        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String bundleKey = base.getBundleKey();

        HighlightType type = mapConditionToType(condition);
        HighlightTarget targetToRender = type.getTarget();

        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, targetToRender)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

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
        log.info("before generate: bundleKey={}, targetToRender={}, condition={}, markedRows={}",
                bundleKey, targetToRender, condition, marked);

        Path out = resolveHighlightedOutputPath(bundleKey, targetToRender, condition);
        generateHighlightedPdf(highlightedRecords, originalPdfPath, out, condition);

        return new FileSystemResource(out);
    }

    private static final Pattern INOUT_ANYWHERE =
            Pattern.compile("(\\d+)[\\(（](\\d+)[\\)）]");

    private List<String> findHospitalizationTokensOnPage(PDDocument document, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);

        String norm = stripper.getText(document).replaceAll("\\s+", "");
        List<String> tokens = new ArrayList<>();

        Matcher m = INOUT_ANYWHERE.matcher(norm);
        while (m.find()) {
            int inpatient = safeParseInt(m.group(1));
            if (inpatient > 0) {
                String token = m.group(0)
                        .replace('（', '(')
                        .replace('）', ')');   // ✅ 괄호 통일

                tokens.add(token);           // 예: "11(0)"
            }
        }
        log.info("[HOSP_TOKEN_SCAN] pageIndex={}, tokens={}", pageIndex, tokens);

        return tokens;
    }

    private int safeParseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }


    @Override
    public Resource downloadExcelByCondition(UUID documentId, int condition) {

        return switch (condition) {
            case 0 -> downloadVisitOver7DaysExcel(documentId);
            case 1 -> downloadDrugOver30DaysExcel(documentId);
            case 2 -> downloadHospitalizationExcel(documentId);
            case 3 -> downloadSurgeryExcel(documentId);
            default -> throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND);
        };
    }

    private Resource downloadVisitOver7DaysExcel(UUID documentId){
        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String bundleKey = base.getBundleKey();

        // ✅ 조건: VISIT_SUMMARY PDF만 사용
        HighlightTarget target = HighlightTarget.VISIT_SUMMARY;

        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, target)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        Path originalPdfPath = Paths.get(uploadDir, targetDoc.getFileUrl());
        if (!Files.exists(originalPdfPath)) {
            throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);
        }

        // 1) PDF 파싱
        List<PdfRowRecord> rows = parseVisitSummaryPdf(originalPdfPath);

        // 2) 병원별 누적 내원일수 계산 -> 7일 이상 병원 키
        Set<String> hitHospitalKeys = findHospitalKeysWith7Days(rows);

        // 3) 해당 병원에 속한 행만 추출
        List<PdfRowRecord> hits = rows.stream()
                .filter(r -> hitHospitalKeys.contains(normalizeHospitalKey(r.getInstitutionName())))
                .sorted(
                        Comparator.comparingInt(PdfRowRecord::getPageIndex)
                                .thenComparing(r -> safe(r.getInstitutionName()))
                )
                .toList();

        // 4) 엑셀 생성 후 Resource 반환
        Path out = resolveExcelOutputPath(bundleKey, "visit7days");
        writeVisit7DaysExcel(hits, out);

        return new FileSystemResource(out);
    }

    private Resource downloadSurgeryExcel(UUID documentId) {
        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));
        String bundleKey = base.getBundleKey();

        HighlightTarget target = HighlightTarget.TREATMENT_DETAIL; // 맞춰서 수정
        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, target)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        Path originalPdfPath = Paths.get(uploadDir, targetDoc.getFileUrl());
        if (!Files.exists(originalPdfPath)) throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);

        List<PdfRowRecord> rows = parseSurgeryPdf(originalPdfPath);

        List<PdfRowRecord> hits = rows.stream()
                .filter(r ->surgeryTokenMatcher.hasRealSurgeryToken(r.getCodeName()))
                .toList();

        Path out = resolveExcelOutputPath(bundleKey, "surgery");
        writeSurgeryExcel(hits, out);

        return new FileSystemResource(out);
    }

    private static final Pattern SURGERY_ROW_START =
            Pattern.compile("^(\\d+)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(.+)$");


    private static final Pattern TRAILING_3_NUMS =
            Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$"); // 1회투약량, 1회투여횟수, 총투약일수(샘플 기준)



    private List<PdfRowRecord> parseSurgeryPdf(Path pdfPath) {
        List<PdfRowRecord> out = new ArrayList<>();

        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            int pages = doc.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);

                String text = stripper.getText(doc);
                String[] lines = text.split("\\r?\\n");

                StringBuilder block = new StringBuilder();
                boolean inBlock = false;

                for (String raw : lines) {
                    String line = (raw == null) ? "" : raw.trim();
                    if (line.isEmpty()) continue;

                    // 헤더 스킵(필요한 만큼 추가)
                    if (line.startsWith("순번")) continue;
                    if (line.contains("진료시작일") && line.contains("코드명")) continue;

                    Matcher start = SURGERY_ROW_START.matcher(line);
                    if (start.find()) {
                        // 새 행 시작 -> 이전 블록 flush
                        if (inBlock) {
                            PdfRowRecord r = parseSurgeryBlock(block.toString(), pageIndex);
                            if (r != null) out.add(r);
                            block.setLength(0);
                        }
                        inBlock = true;
                        block.append(line);
                    } else if (inBlock) {
                        // 같은 행의 이어진 줄
                        block.append(" ").append(line);
                    }
                }

                // 페이지 끝에서 마지막 블록 flush
                if (inBlock && block.length() > 0) {
                    PdfRowRecord r = parseSurgeryBlock(block.toString(), pageIndex);
                    if (r != null) out.add(r);
                }
            }

            return out;

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        }
    }

    private PdfRowRecord parseSurgeryBlock(String rawBlock, int pageIndex) {
        String block = rawBlock.replaceAll("\\s+", " ").trim(); // 줄바꿈/다중 공백 정리

        Matcher start = SURGERY_ROW_START.matcher(block);
        if (!start.find()) return null;

        String seq = start.group(1).trim();
        String startDate = start.group(2).trim();
        String rest = start.group(3).trim(); // 병원명~끝까지

        // 뒤에서 숫자 3개(1회투약량/투여횟수/총투약일수) 떼기
        String dosePerOnce = null;
        String timesPerDay = null;
        String totalDays = null;

        Matcher tail = TRAILING_3_NUMS.matcher(rest);
        if (tail.find()) {
            dosePerOnce = tail.group(1);
            timesPerDay = tail.group(2);
            totalDays = tail.group(3);
            rest = rest.substring(0, tail.start()).trim();
        }

        // ✅ "수 술"처럼 끊긴 케이스도 block 정리 후엔 "수술"이 됨
        // 코드명 판정은 일단 포함 여부로
        boolean hasSurgery = rest.contains("수술");
        if (!hasSurgery) {
            return null; // 수술만 뽑을 거면 여기서 컷
        }

        // rest 앞부분에서 병원명/진료내역/코드명 분리
        // 샘플상 "연세웰치과의원 처치 및 수술/처치 및 수술(양방) ..."
        // → 최소 MVP: 병원명은 첫 토큰, 진료내역은 그 다음 1~몇 토큰, 코드명은 나머지로 두자.
        // (정교화는 실제 데이터 더 보고 조정)
        String[] tokens = rest.split(" ");
        String institutionName = tokens.length > 0 ? tokens[0] : null;

        // 진료내역은 보통 짧음: "처치" 또는 "처치 및 수술/처치 및 수술(양방)" 같은 덩어리
        // 여기선 두 번째 토큰을 treatmentItem로 두고, 나머지를 codeName으로 붙임 (MVP)
        String treatmentItem = tokens.length > 1 ? tokens[1] : null;
        String codeName = (tokens.length > 2) ? String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length)) : null;

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.TREATMENT_DETAIL)
                .rawLine(block)

                .sequence(seq)
                .treatmentStartDate(startDate)
                .institutionName(institutionName)
                .treatmentItem(treatmentItem)
                .codeName(codeName)
                .dosePerOnce(dosePerOnce)
                .timesPerDay(timesPerDay)
                .totalDays(totalDays)

                .treatmentDetail(block) // 디버깅 겸
                .build();
    }

    private void writeSurgeryExcel(List<PdfRowRecord> hits, Path out) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("surgery");

            String[] headers = {
                    "순번", "진료시작일", "병·의원&약국", "진료내역", "코드명",
                    "1회 투약량", "1회 투여횟수", "총 투약일수", "페이지", "원문"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (PdfRowRecord r : hits) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(r.getSequence()));
                row.createCell(1).setCellValue(safe(r.getTreatmentStartDate()));
                row.createCell(2).setCellValue(safe(r.getInstitutionName()));
                row.createCell(3).setCellValue(safe(r.getTreatmentItem()));
                row.createCell(4).setCellValue(safe(r.getCodeName()));
                row.createCell(5).setCellValue(safe(r.getDosePerOnce()));
                row.createCell(6).setCellValue(safe(r.getTimesPerDay()));
                row.createCell(7).setCellValue(safe(r.getTotalDays()));
                row.createCell(8).setCellValue(r.getPageIndex() + 1);
                row.createCell(9).setCellValue(safe(r.getRawLine()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }
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



    private static final java.util.regex.Pattern ROW_START_SEQ_DATE =
            java.util.regex.Pattern.compile("^\\d+\\s+\\d{4}-\\d{2}-\\d{2}\\s+.*"); // "1 2025-04-29 ..."



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

    private static final java.util.regex.Pattern VISIT_SUMMARY_ROW =
            Pattern.compile("^(\\d+)\\s+(.+?)\\s+(\\d+[\\(（]\\d+[\\)）])\\s+([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)\\s*$");

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

        String totalDays = tokens.get(tokens.size() - 1); // 총투약일수

        int start = 2; // seq, date 다음
        int endExclusive = Math.max(start, tokens.size() - 3); // 마지막 3개(투약량/횟수/일수) 앞까지
        String institutionName = join(tokens, start, endExclusive).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.TREATMENT_DETAIL)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .totalDays(totalDays)
                .treatmentDetail(row)
                .build();
    }

    /**
     * 처방조제정보(MVP)
     * - 맨 끝 토큰을 총투약일수로 간주
     */
    private PdfRowRecord parsePrescriptionRow(String row, int pageIndex) {
        String s = row == null ? "" : row.replaceAll("\\s+", " ").trim();
        if (s.isBlank()) return null;

        log.info("[PRESCRIPTION_PARSE] HIT pageIndex={}, head='{}'",
                pageIndex,
                s.substring(0, Math.min(80, s.length()))
        );

        List<String> tokens = Arrays.asList(row.trim().split("\\s+"));
        if (tokens.size() < 6) return null;

        String totalDays = tokens.get(tokens.size() - 1);

        int start = 2; // seq, date 다음
        int endExclusive = Math.max(start, tokens.size() - 3); // 뒤쪽(예: 금액/일수 등) 제외
        String institutionName = join(tokens, start, endExclusive).trim();

        return PdfRowRecord.builder()
                .pageIndex(pageIndex)
                .target(HighlightTarget.PRESCRIPTION)
                .rawLine(row)
                .institutionName(institutionName.isBlank() ? null : institutionName)
                .totalDays(totalDays)
                .treatmentDetail(row)
                .build();
    }

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
            Path outputPdf,
            int condition
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
            PDFTextStripper s = new PDFTextStripper();
            String raw = s.getText(document);
            String norm = raw.replaceAll("\\s+", "");
            log.info("[RAW_CHECK_PDF] file={}, contains11(0)={}",
                    originalPdf.getFileName(),
                    norm.contains("11(0)"));
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

                        if (type == HighlightType.HAS_SURGERY) {
                            log.info("[SURGERY] page={}, text={}", pageIndex, record.getTreatmentDetail());
                        }

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

                            case HAS_SURGERY -> extractSurgeryToken(record.getTreatmentDetail());

                            case MONTH_30_DRUG -> record.getTreatmentDetail();
                        };

                        if (type == HighlightType.HAS_SURGERY) {
                            log.info("[SURGERY_TARGET] page={}, rawTarget='{}'",
                                    pageIndex, rawTarget);
                        }

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

                            highlight.setConstantOpacity(0.9f);
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

                            if (type == HighlightType.HAS_SURGERY) {
                                log.info("[SURGERY_BBOX] page={}, bbox=({}, {}, {}, {})",
                                        pageIndex,
                                        bbox.getLowerLeftX(), bbox.getLowerLeftY(),
                                        bbox.getWidth(), bbox.getHeight());
                            }
                        }
                    }
                }
            }

            if (condition == 2 && highlightCount == 0) {
                highlightCount += applyHospitalizationFallback(document, marks, summaryCounts);
            }

            PdfOverlayRenderer renderer = new PdfOverlayRenderer(document);
            renderer.render(document, marks, summaryCounts);

            document.save(outputPdf.toFile());
            log.info("✅ generateHighlightedPdf END: highlights={}, elapsedMs={}",
                    highlightCount, System.currentTimeMillis() - t0);

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND);
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
        boolean has110 = pageText.contains("11(0)");
        if (has110) {
            log.info("[RAW_CHECK] pageIndex={} has11(0)=true, around='{}'",
                    pageIndex,
                    pageText.substring(
                            Math.max(0, pageText.indexOf("11(0)") - 30),
                            Math.min(pageText.length(), pageText.indexOf("11(0)") + 30)
                    ));
        } else {
            log.info("[RAW_CHECK] pageIndex={} has11(0)=false, head='{}'",
                    pageIndex,
                    pageText.substring(0, Math.min(80, pageText.length())));
        }
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

                // ✅ VISIT_SUMMARY는 기존처럼 한 줄 단위 파싱
                if (target == HighlightTarget.VISIT_SUMMARY) {
                    for (String line : lines) {
                        String row = line.trim();
                        if (row.isEmpty()) continue;

                        PdfRowRecord parsed = parseRowByTarget(target, row, pageIndex);
                        if (parsed != null) rows.add(parsed);
                    }
                    continue;
                }

                // ✅ 나머지: "순번 + 날짜" 시작을 기준으로 여러 줄을 합쳐 한 행(row) 만들기
                StringBuilder buf = new StringBuilder();

                for (String rawLine : lines) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (line.isEmpty()) continue;

                    boolean isNewRow = ROW_START_SEQ_DATE.matcher(line).find();

                    if (isNewRow) {
                        flushBufferedRow(rows, target, buf, pageIndex);
                        buf.append(line);
                    } else {
                        if (!buf.isEmpty()) buf.append(" ");
                        buf.append(line);
                    }
                }

                flushBufferedRow(rows, target, buf, pageIndex);
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

    private int applyHospitalizationFallback(
            PDDocument document,
            List<HighlightMark> marks,
            EnumMap<HighlightType, Integer> summaryCounts
    ) throws IOException {

        log.info("[HOSP_FALLBACK] start");

        int added = 0;
        int pageCount = document.getNumberOfPages();

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            List<String> tokens = findHospitalizationTokensOnPage(document, pageIndex);
            if (tokens.isEmpty()) continue;

            String token = tokens.get(0);
            List<PDRectangle> areas = calculateTextPositions(document, pageIndex, token);
            if (areas == null || areas.isEmpty()) continue;

            PDPage page = document.getPage(pageIndex);
            float pageHeight = page.getMediaBox().getHeight();

            for (PDRectangle rect : areas) {
                PDRectangle bbox = addHighlightAnnotation(page, pageHeight, rect, HighlightType.HAS_HOSPITALIZATION);
                summaryCounts.put(HighlightType.HAS_HOSPITALIZATION,
                        summaryCounts.getOrDefault(HighlightType.HAS_HOSPITALIZATION, 0) + 1);
                marks.add(new HighlightMark(pageIndex, HighlightType.HAS_HOSPITALIZATION, bbox));
                added++;
            }

            log.info("[HOSP_FALLBACK] pageIndex={}, token='{}', rects={}", pageIndex, token, areas.size());
        }

        log.info("[HOSP_FALLBACK] end added={}", added);
        return added;
    }

    private PDRectangle addHighlightAnnotation(PDPage page, float pageHeight, PDRectangle rect, HighlightType type) throws IOException {
        float x1 = rect.getLowerLeftX();
        float y1 = pageHeight - rect.getUpperRightY();
        float x2 = rect.getUpperRightX();
        float y2 = pageHeight - rect.getLowerLeftY();

        PDAnnotationTextMarkup highlight =
                new PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);

        highlight.setConstantOpacity(0.95f);
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

        return bbox;
    }

    private void flushBufferedRow(List<PdfRowRecord> rows, HighlightTarget target, StringBuilder buf, int pageIndex) {
        if (buf == null || buf.isEmpty()) return;

        String row = buf.toString().trim();
        buf.setLength(0);

        // VISIT_SUMMARY는 기존 로직 유지
        if (target != HighlightTarget.VISIT_SUMMARY) {
            // "1 2025-04-29 ..." 형태가 아니면(헤더/면책/페이지정보) 버림
            if (!ROW_START_SEQ_DATE.matcher(row).find()) return;
        }

        PdfRowRecord parsed = parseRowByTarget(target, row, pageIndex);
        if (parsed != null) rows.add(parsed);
    }

//    private static final Pattern SURGERY_TOKEN =
//            Pattern.compile("([가-힣A-Za-z0-9\\[\\]\\(\\)\\/\\-]{2,}수술)(?=\\d|$)");

    private String extractSurgeryToken(String rowText) {
        log.info("[EXTRACT_IMPL] SIMPLE_LAST_SURGERY");

        if (rowText == null) return null;
        String s = rowText.replaceAll("\\s+", "");

        if (s.contains("수술후처치") || s.contains("단순처치")) return null;

        int idx = s.lastIndexOf("수술");
        if (idx < 0) return null;

        // "수술" 앞의 12글자 정도만 가져오자(대부분 '...근치수술' 같은 길이)
        int start = Math.max(0, idx - 12);
        String token = s.substring(start, idx + 2);

// 괄호 제거
        token = token.replaceAll("[\\(\\)]", "");

// 분류어 제거 (매칭 실패 방지)
        token = token.replace("양방", "")
                .replace("한방", "")
                .replace("치과", "");

// 앞뒤 정리
        token = token.replaceAll("^[/\\-]+", "");

        log.info("[EXTRACT_SURGERY_TOKEN] token='{}'", token);
        return token;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void writeVisit7DaysExcel(List<PdfRowRecord> rows, Path out) {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("7일이상내원");


            // ✅ VISIT_SUMMARY 표 컬럼
            String[] headers = {
                    "순번",
                    "병·의원&약국",
                    "입원(외래)일수",
                    "총 진료비(건강보험 적용분)",
                    "건강보험 등 혜택받은 금액",
                    "내가 낸 의료비(진료비)",
                    "페이지",
                    "원문"
            };

            // ✅ 헤더는 0행에 한 줄만
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // ✅ 데이터는 1행부터
            int rowIdx = 1;
            for (PdfRowRecord r : rows) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(safe(r.getSequence()));
                row.createCell(1).setCellValue(safe(r.getInstitutionName()));
                row.createCell(2).setCellValue(safe(r.getDaysOfStayOrVisit()));
                row.createCell(3).setCellValue(safe(r.getTotalMedicalFee()));
                row.createCell(4).setCellValue(safe(r.getInsuranceBenefit()));
                row.createCell(5).setCellValue(safe(r.getUserPaidAmount()));
                row.createCell(6).setCellValue(r.getPageIndex() + 1);
                row.createCell(7).setCellValue(safe(r.getRawLine()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }
    }


    private Path resolveExcelOutputPath(String bundleKey, String text) {
        Path dir = Paths.get(uploadDir, "excel");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }

        String safeBundleKey = bundleKey.replaceAll("[^a-zA-Z0-9\\-]", "");
        String safeText = (text == null ? "out" : text.replaceAll("[^a-zA-Z0-9\\-]", ""));

        String fileName = String.format("%s-%s-%s-%s.xlsx",
                safeBundleKey,
                safeText,
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                java.util.UUID.randomUUID().toString().substring(0, 8)   // ✅ 캐시/덮어쓰기 방지
        );

        return dir.resolve(fileName);
    }

    private List<PdfRowRecord> parseVisitSummaryPdf(Path pdfPath) {
        List<PdfRowRecord> out = new ArrayList<>();

        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();

            int pages = doc.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);

                String text = stripper.getText(doc);
                String[] lines = text.split("\\r?\\n");

                StringBuilder buf = new StringBuilder();
                boolean buffering = false;

                for (String raw : lines) {
                    String line = (raw == null) ? "" : raw.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("순번")) continue;
                    if (line.contains("병·의원&약국")) continue;
                    if (line.startsWith("진료내용")) continue;
                    if (line.startsWith("총 진료비")) continue;
                    if (line.startsWith("(건강보험")) continue;
                    if (line.startsWith("건강보험")) continue;
                    if (line.startsWith("혜택받은")) continue;
                    if (line.startsWith("내가 낸")) continue;

                    boolean seqOnly = line.matches("^\\d+$");
                    boolean seqWithText = line.matches("^\\d+\\s+.*");
                    boolean startsRow = seqOnly || seqWithText;

                    if (startsRow) {
                        buf.setLength(0);
                        buf.append(line);
                        buffering = true;
                    } else if (buffering) {
                        buf.append(" ").append(line);
                    } else {
                        continue;
                    }

                    String merged = buf.toString().replaceAll("\\s+", " ").trim();

                    Matcher m = VISIT_SUMMARY_ROW.matcher(merged);
                    if (!m.find()) continue;

                    String instRaw = m.group(2).trim().replaceAll("\\s+", " ");
                    String[] parts = instRaw.split(" ");

                    StringBuilder inst = new StringBuilder();
                    boolean first = true;

                    for (int i = 0; i < parts.length; i++) {
                        String cur = parts[i];
                        String prev = (i > 0) ? parts[i - 1] : null;

                        boolean curIsOneHangul = cur.length() == 1 && cur.matches("[가-힣]");
                        boolean prevIsOneHangul = prev != null && prev.length() == 1 && prev.matches("[가-힣]");

                        if (first) {
                            inst.append(cur);
                            first = false;
                            continue;
                        }

                        if (curIsOneHangul && prevIsOneHangul) inst.append(cur);
                        else inst.append(' ').append(cur);
                    }

                    PdfRowRecord r = PdfRowRecord.builder()
                            .pageIndex(pageIndex)
                            .target(HighlightTarget.VISIT_SUMMARY)
                            .rawLine(merged)
                            .sequence(m.group(1).trim())
                            .institutionName(m.group(2).trim())
                            .daysOfStayOrVisit(m.group(3).trim())
                            .totalMedicalFee(m.group(4).trim())
                            .insuranceBenefit(m.group(5).trim())
                            .userPaidAmount(m.group(6).trim())
                            .treatmentDetail(null)
                            .build();

                    out.add(r);

                    buf.setLength(0);
                    buffering = false;
                }
            }

            return out;

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
        }
    }

    private Resource downloadHospitalizationExcel(UUID documentId) {

        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String bundleKey = base.getBundleKey();
        HighlightTarget target = HighlightTarget.VISIT_SUMMARY;

        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, target)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        Path originalPdfPath = Paths.get(uploadDir, targetDoc.getFileUrl());
        if (!Files.exists(originalPdfPath)) throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);

        List<PdfRowRecord> rows = parseVisitSummaryPdf(originalPdfPath);

        List<PdfRowRecord> hits = rows.stream()
                .filter(r -> !isPharmacy(r.getInstitutionName()))
                .filter(r -> {
                    String v = safe(r.getDaysOfStayOrVisit()).replaceAll("\\s+", "");
                    Matcher m = INOUT_ANYWHERE.matcher(v);
                    return m.find() && safeParseInt(m.group(1)) > 0;
                })
                .sorted(Comparator.comparingInt(PdfRowRecord::getPageIndex)
                        .thenComparing(r -> safe(r.getInstitutionName())))
                .toList();

        log.info("[HOSP_EXCEL] hitsByRowParse={}", hits.size());

        if (hits.isEmpty()) {
            log.info("[HOSP_EXCEL_FALLBACK] use token-scan + reconstruct row");

            try (PDDocument doc = PDDocument.load(originalPdfPath.toFile())) {

                PDFTextStripper stripper = new PDFTextStripper();
                List<PdfRowRecord> fallbackHits = new ArrayList<>();

                int pages = doc.getNumberOfPages();
                for (int pageIndex = 0; pageIndex < pages; pageIndex++) {

                    List<String> tokens = findHospitalizationTokensOnPage(doc, pageIndex);
                    if (tokens.isEmpty()) continue;

                    stripper.setStartPage(pageIndex + 1);
                    stripper.setEndPage(pageIndex + 1);
                    String pageText = stripper.getText(doc);
                    String[] lines = pageText.split("\\r?\\n");

                    StringBuilder buf = new StringBuilder();
                    boolean buffering = false;

                    for (String raw : lines) {
                        String line = (raw == null) ? "" : raw.trim();
                        if (line.isEmpty()) continue;

                        if (line.startsWith("순번")) continue;
                        if (line.contains("병·의원&약국")) continue;
                        if (line.startsWith("진료내용")) continue;
                        if (line.startsWith("총 진료비")) continue;
                        if (line.startsWith("(건강보험")) continue;
                        if (line.startsWith("건강보험")) continue;
                        if (line.startsWith("혜택받은")) continue;
                        if (line.startsWith("내가 낸")) continue;

                        boolean seqOnly = line.matches("^\\d+$");
                        boolean seqWithText = line.matches("^\\d+\\s+.*");
                        boolean startsRow = seqOnly || seqWithText;

                        if (startsRow) {
                            if (buffering && buf.length() > 0) {
                                String block = buf.toString().replaceAll("\\s+", " ").trim();

                                boolean containsToken = false;
                                for (String t : tokens) {
                                    if (block.contains(t)) { containsToken = true; break; }
                                    String t2 = t.replace('(', '（').replace(')', '）');
                                    if (block.contains(t2)) { containsToken = true; break; }
                                }

                                if (containsToken) {
                                    Matcher m = VISIT_SUMMARY_ROW.matcher(block);
                                    if (m.find()) {
                                        String inout = m.group(3).trim();
                                        Matcher in = INOUT_ANYWHERE.matcher(inout.replaceAll("\\s+", ""));
                                        if (in.find() && safeParseInt(in.group(1)) > 0) {
                                            fallbackHits.add(PdfRowRecord.builder()
                                                    .pageIndex(pageIndex)
                                                    .target(HighlightTarget.VISIT_SUMMARY)
                                                    .rawLine(block)
                                                    .sequence(m.group(1).trim())
                                                    .institutionName(m.group(2).trim())
                                                    .daysOfStayOrVisit(inout)
                                                    .totalMedicalFee(m.group(4).trim())
                                                    .insuranceBenefit(m.group(5).trim())
                                                    .userPaidAmount(m.group(6).trim())
                                                    .treatmentDetail(null)
                                                    .build());
                                        }
                                    }
                                }
                            }

                            buf.setLength(0);
                            buf.append(line);
                            buffering = true;

                        } else if (buffering) {
                            buf.append(" ").append(line);
                        }
                    }

                    if (buffering && buf.length() > 0) {
                        String block = buf.toString().replaceAll("\\s+", " ").trim();

                        boolean containsToken = false;
                        for (String t : tokens) {
                            if (block.contains(t)) { containsToken = true; break; }
                            String t2 = t.replace('(', '（').replace(')', '）');
                            if (block.contains(t2)) { containsToken = true; break; }
                        }

                        if (containsToken) {
                            Matcher m = VISIT_SUMMARY_ROW.matcher(block);
                            if (m.find()) {
                                String inout = m.group(3).trim();
                                Matcher in = INOUT_ANYWHERE.matcher(inout.replaceAll("\\s+", ""));
                                if (in.find() && safeParseInt(in.group(1)) > 0) {
                                    fallbackHits.add(PdfRowRecord.builder()
                                            .pageIndex(pageIndex)
                                            .target(HighlightTarget.VISIT_SUMMARY)
                                            .rawLine(block)
                                            .sequence(m.group(1).trim())
                                            .institutionName(m.group(2).trim())
                                            .daysOfStayOrVisit(inout)
                                            .totalMedicalFee(m.group(4).trim())
                                            .insuranceBenefit(m.group(5).trim())
                                            .userPaidAmount(m.group(6).trim())
                                            .treatmentDetail(null)
                                            .build());
                                }
                            }
                        }
                    }
                }

                hits = fallbackHits;
                log.info("[HOSP_EXCEL_FALLBACK] reconstructedHits={}", hits.size());

            } catch (IOException e) {
                throw new BaseException(ExceptionEnum.FILE_READ_ERROR);
            }
        }

        Path out = resolveExcelOutputPath(bundleKey, "hospitalization");
        writeHospitalizationExcel(hits, out);

        return new FileSystemResource(out);
    }

    private void writeHospitalizationExcel(List<PdfRowRecord> rows, Path out) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("입원내역");

            String[] headers = {
                    "순번",
                    "병·의원&약국",
                    "입원(외래)일수",
                    "총 진료비(건강보험 적용분)",
                    "건강보험 등 혜택받은 금액",
                    "내가 낸 의료비(진료비)",
                    "페이지",
                    "원문"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (PdfRowRecord r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(r.getSequence()));
                row.createCell(1).setCellValue(safe(r.getInstitutionName()));
                row.createCell(2).setCellValue(safe(r.getDaysOfStayOrVisit()));
                row.createCell(3).setCellValue(safe(r.getTotalMedicalFee()));
                row.createCell(4).setCellValue(safe(r.getInsuranceBenefit()));
                row.createCell(5).setCellValue(safe(r.getUserPaidAmount()));
                row.createCell(6).setCellValue(r.getPageIndex() + 1);
                row.createCell(7).setCellValue(safe(r.getRawLine()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }
    }

    private static final int THRESHOLD_DAYS = 30;

    public Resource downloadDrugOver30DaysExcel(UUID documentId) {


        Document base = documentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        String bundleKey = base.getBundleKey();

        HighlightTarget target = HighlightTarget.PRESCRIPTION;

        Document targetDoc = documentRepository.findByBundleKeyAndTarget(bundleKey, target)
                .orElseThrow(() -> new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND));

        Path originalPdfPath = Paths.get(uploadDir, targetDoc.getFileUrl());
        if (!Files.exists(originalPdfPath)) throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);

        // 1) PDF 파싱
        List<PdfRowRecord> rows = parsePdfToRows(originalPdfPath, target);

        // 2) PRESCRIPTION 대상만
        List<PdfRowRecord> drugRows = rows.stream()
                .filter(r -> r.getTarget() == HighlightTarget.PRESCRIPTION)
                .toList();

        // 3) 같은 날짜 + 같은 약(성분+약품명) 기준으로
        //    처방조제 우선, 없으면 외래
        Map<String, PdfRowRecord> pickedByDayDrug = new HashMap<>();

        for (PdfRowRecord r : drugRows) {
            String date = safe(r.getTreatmentStartDate());
            String ingredient = normalizeDrugKey(r.getCodeName());
            String drugName = normalizeDrugKey(r.getTreatmentItem());

            if (date.isBlank() || ingredient.isBlank() || drugName.isBlank()) continue;

            String dayDrugKey = date + "|" + ingredient + "|" + drugName;

            log.info("[DAY_DRUG_KEY] type={}, key={}",
                    safe(r.getRawLine()).contains("처방조제") ? "처방조제" : "외래",
                    dayDrugKey
            );

            PdfRowRecord prev = pickedByDayDrug.get(dayDrugKey);
            if (prev == null) {
                pickedByDayDrug.put(dayDrugKey, r);
                continue;
            }

            boolean prevIsDispense = safe(prev.getRawLine()).contains("처방조제");
            boolean currIsDispense = safe(r.getRawLine()).contains("처방조제");

            // 기존이 외래이고, 현재가 처방조제면 교체
            if (!prevIsDispense && currIsDispense) {
                pickedByDayDrug.put(dayDrugKey, r);
            }
        }

        List<PdfRowRecord> pickedRows = new ArrayList<>(pickedByDayDrug.values());

        // 4) 누적 집계 (기존 메서드 그대로 사용)
        Map<String, Integer> sumByDrug = sumTotalDaysByDrugKey(pickedRows);

        // 5) 30일 이상 약 키
        Set<String> hitKeys = sumByDrug.entrySet().stream()
                .filter(e -> e.getValue() >= THRESHOLD_DAYS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // 6) 근거 row만
        List<PdfRowRecord> hitRows = pickedRows.stream()
                .filter(r -> {
                    String key = normalizeDrugKey(r.getCodeName()) + "|" +
                            normalizeDrugKey(r.getTreatmentItem());
                    return hitKeys.contains(key);
                })
                .sorted(Comparator.comparingInt(PdfRowRecord::getPageIndex)
                        .thenComparing(r -> safe(r.getInstitutionName()))
                        .thenComparing(r -> safe(r.getTreatmentStartDate())))
                .toList();

        // 7) 엑셀 생성
        Path out = resolveExcelOutputPath(bundleKey, "drug30days");
        writeDrugOver30DaysExcel(hitRows, sumByDrug, out);

        return new FileSystemResource(out);
    }

    private void writeDrugOver30DaysExcel(List<PdfRowRecord> hits, Map<String, Integer> sumByDrug, Path out) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("30일초과약제");

            String[] headers = {
                    "순번",
                    "진료시작일",
                    "병·의원&약국",
                    "약품명",
                    "성분명",
                    "1회 투약량",
                    "1회 투여횟수",
                    "총 투약일수",
                    "누적 투약일수",
                    "페이지",
                    "원문"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (PdfRowRecord r : hits) {
                Row row = sheet.createRow(rowIdx++);

                String key = normalizeDrugKey(r.getCodeName()) + "|" + normalizeDrugKey(r.getTreatmentItem());
                int totalSum = sumByDrug.getOrDefault(key, 0);

                row.createCell(0).setCellValue(safe(r.getSequence()));
                row.createCell(1).setCellValue(safe(r.getTreatmentStartDate()));
                row.createCell(2).setCellValue(safe(r.getInstitutionName()));
                row.createCell(3).setCellValue(safe(r.getTreatmentItem()));  // 약품명
                row.createCell(4).setCellValue(safe(r.getCodeName()));       // 성분명
                row.createCell(5).setCellValue(safe(r.getDosePerOnce()));
                row.createCell(6).setCellValue(safe(r.getTimesPerDay()));
                row.createCell(7).setCellValue(safe(r.getTotalDays()));
                row.createCell(8).setCellValue(totalSum);
                row.createCell(9).setCellValue(r.getPageIndex() + 1);
                row.createCell(10).setCellValue(safe(r.getRawLine()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }

        } catch (IOException e) {
            throw new BaseException(ExceptionEnum.FILE_WRITE_ERROR);
        }
    }

    private Map<String, Integer> sumTotalDaysByDrugKey(List<PdfRowRecord> drugRows) {
        Map<String, Integer> sumByDrug = new HashMap<>();

        for (PdfRowRecord r : drugRows) {
            String ingredient = normalizeDrugKey(r.getCodeName());       // 성분명
            String drugName = normalizeDrugKey(r.getTreatmentItem());    // 약품명

            if (ingredient.isBlank() || drugName.isBlank()) continue;

            String key = ingredient + "|" + drugName;

            int days = parsePositiveInt(r.getTotalDays());
            if (days <= 0) continue;

            sumByDrug.merge(key, days, Integer::sum);
        }
        return sumByDrug;
    }

    private String normalizeDrugKey(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").replace("_", "");
    }

    private int parsePositiveInt(String s) {
        if (s == null) return 0;
        String n = s.replaceAll("[^0-9]", "");
        if (n.isBlank()) return 0;
        try {
            return Integer.parseInt(n);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}