package com.project.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.backend.models.EmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailGeneratorService {

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {
        this.apiKey = geminiApiKey;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        String prompt = buildPrompt(emailRequest);

        String requestBody;
        try {
            // Safely serialize body instead of String.format
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }

        String response = webClient.post()
                .uri("/v1beta/models/gemini-2.0-flash:generateContent")
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Gemini API error: " + body))
                )
                .bodyToMono(String.class)
                .block();

        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode textNode = candidates
                        .get(0)
                        .path("content")
                        .path("parts")
                        .get(0)
                        .path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText();
                }
            }
            return "No reply generated.";
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Gemini API response", e);
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a professional email reply for the following email. ");
        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
        }
        prompt.append("Original Email:\n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}
