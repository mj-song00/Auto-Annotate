package auto.annotate.domain.pdf.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PdfController {

    @GetMapping("/")
    public String pdfPreview() {

        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

}