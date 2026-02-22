package com.cv.aiml_project.service;

import com.cv.aiml_project.entity.Resume;
import com.cv.aiml_project.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AIMLIntegrationService {

    @Value("${ai.api.url:http://localhost:5000/api}")
    private String aiApiUrl;

    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Autowired
    private ResumeRepository resumeRepository;  // Use repository directly instead of service

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Process a resume with the AI/ML API
     */
    public void processResume(Long resumeId, MultipartFile file) throws IOException {
        // Save file temporarily if needed
        Path tempFile = Files.createTempFile("resume_", ".pdf");
        file.transferTo(tempFile.toFile());

        try {
            // Prepare API request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            if (aiApiKey != null && !aiApiKey.isEmpty()) {
                headers.set("X-API-Key", aiApiKey);
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("resume", new FileSystemResource(tempFile.toFile()));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Call AI API
            String apiUrl = aiApiUrl + "/analyze-resume";
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            // Process response
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();

                Double score = ((Number) result.getOrDefault("score", 0.0)).doubleValue();
                Double confidence = ((Number) result.getOrDefault("confidence", 0.0)).doubleValue();
                String extractedText = (String) result.getOrDefault("extracted_text", "");
                String rawResponse = response.getBody().toString();

                // Update resume with AI results directly using repository
                updateResumeWithAIResults(resumeId, score, confidence, extractedText, rawResponse);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI processing failed: " + e.getMessage());
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Update resume with AI results directly
     */
    private void updateResumeWithAIResults(Long resumeId, Double score, Double confidence,
                                           String extractedText, String rawResponse) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        resume.setMlProcessed(true);
        resume.setMlScore(score);
        resume.setMlConfidence(confidence);
        resume.setMlProcessedDate(LocalDateTime.now());

        if (extractedText != null) {
            resume.setExtractedText(extractedText);
        }

        if (rawResponse != null) {
            resume.setMlRawResponse(rawResponse);
        }

        resumeRepository.save(resume);
    }

    /**
     * Match a resume against a job description
     */
    public Map<String, Object> matchResumeWithJob(Long resumeId, Long jobId, String jobDescription) {
        try {
            Resume resume = resumeRepository.findById(resumeId)
                    .orElseThrow(() -> new RuntimeException("Resume not found"));

            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (aiApiKey != null && !aiApiKey.isEmpty()) {
                headers.set("X-API-Key", aiApiKey);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("resume_text", resume.getExtractedText());
            requestBody.put("job_description", jobDescription);
            requestBody.put("job_id", jobId);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call API
            String apiUrl = aiApiUrl + "/match";
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get match score from AI service");
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI matching failed: " + e.getMessage());
        }
    }

    /**
     * Process all unprocessed resumes
     */
    public void processAllUnprocessedResumes() {
        // This method would need to be called from a scheduler with access to the files
        // You might want to implement this differently
    }
}