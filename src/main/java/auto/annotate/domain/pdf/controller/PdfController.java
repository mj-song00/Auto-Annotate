package auto.annotate.domain.pdf.controller;

import auto.annotate.domain.document.repository.DocumentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import auto.annotate.domain.document.entity.Document;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PdfController {

    private final DocumentRepository documentRepository;

    public PdfController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping("/")
    public String pdfPreview(Model model) {

        List<UUID> documentIds = documentRepository.findAll()
                .stream()
                .map(Document::getId)
                .collect(Collectors.toList());

        model.addAttribute("documentIds", documentIds);
        return "pdf_preview";
    }

    /**
     * ✅ 타임리프 미리보기 페이지 렌더링
     * - bundleKey 기준으로 해당 묶음의 documentIds를 내려주고
     * - JS에서 쓸 bundleKey도 같이 내려준다.
     *
     * 호출 예: GET /pdf/preview/{bundleKey}
     */
    @GetMapping("/preview/{bundleKey}")
    public String preview(@PathVariable String bundleKey, Model model) {

        List<UUID> documentIds = documentRepository.findByBundleKey(bundleKey)
                .stream()
                .map(Document::getId)
                .toList();

        model.addAttribute("bundleKey", bundleKey);
        model.addAttribute("documentIds", documentIds);

        return "pdf_preview";
    }
}