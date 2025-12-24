package auto.annotate.domain.pdf.controller;

import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                .toList();

        model.addAttribute("documentIds", documentIds);

        return "pdf_preview";
    }
}