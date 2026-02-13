package auto.annotate.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class DiscordNotifier {

    @Value("${discord.url}")
    private String webhookUrl;

    private final RestClient restClient = RestClient.create();

    public void sendError(String message) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.out.println("Discord 알림 전송 실패: " + e.getMessage());
        }
    }
}
