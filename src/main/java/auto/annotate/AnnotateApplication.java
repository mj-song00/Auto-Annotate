package auto.annotate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AnnotateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnnotateApplication.class, args);
    }

}
