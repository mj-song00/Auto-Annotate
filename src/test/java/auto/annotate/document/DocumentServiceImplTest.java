package auto.annotate.document;

import auto.annotate.common.entity.ExcelJobLog;
import auto.annotate.common.enums.JobStatus;
import auto.annotate.common.exception.BaseException;
import auto.annotate.common.repository.ExcelJobLogRepository;
import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.document.service.DocumentServiceImpl;
import auto.annotate.domain.folder.dto.request.SaveFolderRequest;
import auto.annotate.domain.folder.entity.Folder;
import auto.annotate.domain.folder.repository.FolderRepository;
import auto.annotate.domain.user.dto.AuthUser;
import auto.annotate.domain.user.entity.User;
import auto.annotate.domain.user.service.UserServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ExcelJobLogRepository excelJobLogRepository;
    @Mock private UserServiceImpl userService;
    @Mock private S3Client s3Client;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @TempDir
    Path tempDir;

    @Captor
    ArgumentCaptor<ExcelJobLog> excelJobLogCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "bucket", "test-bucket");
    }

    @Test
    @DisplayName("폴더명이 공백이면 파일저장이 실행되지 않고 예외가 발생한다")
    void save_invalidFolderName_throws() {
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getId()).thenReturn(UUID.randomUUID());
        when(userService.findByIdOrThrow(any())).thenReturn(mock(User.class));

        List<org.springframework.web.multipart.MultipartFile> files =
                List.of(new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[]{1}));

        SaveFolderRequest req = new SaveFolderRequest();
        ReflectionTestUtils.setField(req, "name", "   ");

        assertThatThrownBy(() -> documentService.save(files, authUser, req))
                .isInstanceOf(BaseException.class);

        verify(folderRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    @DisplayName("동일한 하이라이트 파일이 두개이면 두번째는 저장되지 않는다")
    void save_sameTargetAppearsTwice_secondIsSkipped_andOnlyOneDocumentSaved() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getId()).thenReturn(userId);

        User user = mock(User.class);
        when(userService.findByIdOrThrow(userId)).thenReturn(user);

        Folder folder = mock(Folder.class);
        when(folderRepository.save(any())).thenReturn(folder);

        byte[] visitSummaryPdf;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                PDType0Font font = PDType0Font.load(doc, fontIs);

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("진료정보요약");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            visitSummaryPdf = baos.toByteArray();
        }

        MockMultipartFile f1 = new MockMultipartFile("f", "a.pdf", "application/pdf", visitSummaryPdf);
        MockMultipartFile f2 = new MockMultipartFile("f", "b.pdf", "application/pdf", visitSummaryPdf);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("u1").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag1").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveFolderRequest req = new SaveFolderRequest();
        ReflectionTestUtils.setField(req, "name", "폴더");

        List<Document> saved = documentService.save(List.of(f1, f2), authUser, req);

        assertThat(saved).hasSize(1);
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("S3 Multipart업로드 중 실패하면 abort가 호출되고 DB저장은 되지않는다")
    void save_whenUploadPartFails_abortMultipartUploadIsCalled_andThrows() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getId()).thenReturn(userId);

        User user = mock(User.class);
        when(userService.findByIdOrThrow(userId)).thenReturn(user);

        when(folderRepository.save(any())).thenReturn(mock(Folder.class));

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                PDType0Font font = PDType0Font.load(doc, fontIs);

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("진료정보요약");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile f1 = new MockMultipartFile("f", "a.pdf", "application/pdf", pdfBytes);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("u1").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("boom"));

        SaveFolderRequest req = new SaveFolderRequest();
        ReflectionTestUtils.setField(req, "name", "폴더");

        assertThatThrownBy(() -> documentService.save(List.of(f1), authUser, req))
                .isInstanceOf(BaseException.class);

        verify(s3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("수술조건 엑셀다운로드시 엑셀파일이 생성되고 로그가 SUCCESS로 저장된다")
    void downloadExcelByCondition_condition3_savesExcelJobLogSuccess_andReturnsXlsxResource() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String bundleKey = "bundle-1";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        Document base = mock(Document.class);
        when(base.getBundleKey()).thenReturn(bundleKey);
        when(base.getUser()).thenReturn(user);

        Document targetDoc = mock(Document.class);
        when(targetDoc.getFileUrl()).thenReturn(bundleKey + "/x.pdf");

        when(documentRepository.findById(docId)).thenReturn(Optional.of(base));
        when(documentRepository.findByBundleKeyAndTarget(bundleKey, HighlightTarget.TREATMENT_DETAIL))
                .thenReturn(Optional.of(targetDoc));

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                PDType0Font font = PDType0Font.load(doc, fontIs);

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("1 2025-04-29 연세웰치과의원 처치 및 수술/처치 및 수술(양방) 수술 1 1 3");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        ResponseBytes<GetObjectResponse> rb = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength((long) pdfBytes.length).build(),
                pdfBytes
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(rb);

        doAnswer(inv -> inv.getArgument(0))
                .when(excelJobLogRepository).save(excelJobLogCaptor.capture());

        Resource res = documentService.downloadExcelByCondition(docId, 3);

        assertThat(res).isNotNull();
        assertThat(res.getFilename()).endsWith(".xlsx");

        ExcelJobLog savedLog = excelJobLogCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(savedLog.getCondition()).isEqualTo(3);
        assertThat(savedLog.getBundleKey()).isEqualTo(bundleKey);

        assertThat(res.exists()).isTrue();
    }

    @Test
    @DisplayName("첫 페이지 키워드에 따라 HighlightTarget이 올바르게 판별된다")
    void detectHighlightTarget_shouldReturnCorrectTarget_basedOnFirstPageKeyword() throws Exception {

        byte[] b1;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType0Font font = PDType0Font.load(doc, fontIs);
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("진료정보요약");
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            b1 = baos.toByteArray();
        }
        Path p1 = tempDir.resolve("visit.pdf");
        Files.write(p1, b1);
        HighlightTarget t1 = ReflectionTestUtils.invokeMethod(documentService, "detectHighlightTargetFromFile", p1);
        assertThat(t1).isEqualTo(HighlightTarget.VISIT_SUMMARY);

        byte[] b2;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType0Font font = PDType0Font.load(doc, fontIs);
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("기본진료정보");
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            b2 = baos.toByteArray();
        }
        Path p2 = tempDir.resolve("drug.pdf");
        Files.write(p2, b2);
        HighlightTarget t2 = ReflectionTestUtils.invokeMethod(documentService, "detectHighlightTargetFromFile", p2);
        assertThat(t2).isEqualTo(HighlightTarget.DRUG_SUMMARY);

        byte[] b3;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType0Font font = PDType0Font.load(doc, fontIs);
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("세부진료정보");
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            b3 = baos.toByteArray();
        }
        Path p3 = tempDir.resolve("detail.pdf");
        Files.write(p3, b3);
        HighlightTarget t3 = ReflectionTestUtils.invokeMethod(documentService, "detectHighlightTargetFromFile", p3);
        assertThat(t3).isEqualTo(HighlightTarget.TREATMENT_DETAIL);

        byte[] b4;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType0Font font = PDType0Font.load(doc, fontIs);
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("처방조제정보");
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            b4 = baos.toByteArray();
        }
        Path p4 = tempDir.resolve("prescription.pdf");
        Files.write(p4, b4);
        HighlightTarget t4 = ReflectionTestUtils.invokeMethod(documentService, "detectHighlightTargetFromFile", p4);
        assertThat(t4).isEqualTo(HighlightTarget.PRESCRIPTION);
    }

    @Test
    @DisplayName("30일 초과 약제 조건: 동일 약품 누적 투약일수가 30일 이상이면 엑셀에 포함된다")
    void downloadExcelByCondition_shouldIncludeDrug_whenTotalDaysMeetOrExceed30() throws Exception {

        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String bundleKey = "bundle-1";
        String s3Key = bundleKey + "/prescription.pdf";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        Document base = mock(Document.class);
        when(base.getBundleKey()).thenReturn(bundleKey);
        when(base.getUser()).thenReturn(user);

        Document targetDoc = mock(Document.class);
        when(targetDoc.getFileUrl()).thenReturn(s3Key);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(base));
        when(documentRepository.findByBundleKeyAndTarget(bundleKey, HighlightTarget.PRESCRIPTION))
                .thenReturn(Optional.of(targetDoc));

        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (InputStream fontIs = new ClassPathResource("fonts/NotoSansKR-VariableFont_wght.ttf").getInputStream();
                 PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                PDType0Font font = PDType0Font.load(doc, fontIs);

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);

                cs.showText("1 2025-04-29 서울병원 외래 처방조제 아스피린정(100mg) 1 1 20");
                cs.newLineAtOffset(0, -18);
                cs.showText("2 2025-05-10 서울병원 외래 처방조제 아스피린정(100mg) 1 1 15");

                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            pdfBytes = baos.toByteArray();
        }

        ResponseBytes<GetObjectResponse> rb = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength((long) pdfBytes.length).build(),
                pdfBytes
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(rb);

        Resource res = documentService.downloadExcelByCondition(docId, 1);

        assertThat(res).isNotNull();
        assertThat(res.exists()).isTrue();
        assertThat(res.getFilename()).endsWith(".xlsx");
    }
}