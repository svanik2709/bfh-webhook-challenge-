package com.example.webhookapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class WebhookService implements CommandLineRunner {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        // Step 1: Call generateWebhook API
        String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "John Doe");
        requestBody.put("regNo", "REG12395");  // Replace with your regNo
        requestBody.put("email", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode root = objectMapper.readTree(response.getBody());

            String webhookUrl = root.path("webhook").asText();
            String accessToken = root.path("accessToken").asText();
            JsonNode usersData = root.path("data").path("users");

            // Step 2: Process data - find mutual followers
            List<List<Integer>> mutualFollowers = findMutualFollowers(usersData);

            // Step 3: Prepare output body
            Map<String, Object> output = new HashMap<>();
            output.put("regNo", "REG12395");
            output.put("outcome", mutualFollowers);

            // Step 4: Send result to webhook
            sendToWebhook(webhookUrl, accessToken, output);
        } else {
            System.out.println("Failed to call generateWebhook: " + response.getStatusCode());
        }
    }

    private List<List<Integer>> findMutualFollowers(JsonNode users) {
        Map<Integer, Set<Integer>> followMap = new HashMap<>();

        for (JsonNode user : users) {
            int id = user.path("id").asInt();
            Set<Integer> follows = new HashSet<>();

            JsonNode followsNode = user.path("follows");
            if (followsNode != null && followsNode.isArray()) {
                for (JsonNode f : followsNode) {
                    follows.add(f.asInt());
                }
            }

            followMap.put(id, follows);
        }

        List<List<Integer>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int u1 : followMap.keySet()) {
            for (int u2 : followMap.get(u1)) {
                if (followMap.containsKey(u2) && followMap.get(u2).contains(u1)) {
                    int min = Math.min(u1, u2);
                    int max = Math.max(u1, u2);
                    String key = min + ":" + max;
                    if (!seen.contains(key)) {
                        result.add(Arrays.asList(min, max));
                        seen.add(key);
                    }
                }
            }
        }

        return result;
    }

    private void sendToWebhook(String webhookUrl, String accessToken, Map<String, Object> body) throws InterruptedException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Important: Use raw token (NO Bearer prefix)
        headers.set("Authorization", accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int attempts = 0;
        while (attempts < 4) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    System.out.println("✅ Successfully posted to webhook!");
                    break;
                } else {
                    System.out.println("❌ Failed attempt " + (attempts + 1) + ": " + response.getStatusCode());
                }
            } catch (Exception e) {
                System.out.println("⚠️ Error on attempt " + (attempts + 1) + ": " + e.getMessage());
            }
            attempts++;
            Thread.sleep(1000); // wait before retry
        }
    }
}
