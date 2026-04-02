package com.jimbro.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jimbro.dto.ChatRequest;
import com.jimbro.dto.ChatResponse;
import com.jimbro.dto.GeneratePlanRequest;
import com.jimbro.dto.GeneratePlanResponse;
import com.jimbro.dto.ReviseDietRequest;
import com.jimbro.entity.ChatMessage;
import com.jimbro.entity.FitnessLog;
import com.jimbro.repository.ChatMessageRepository;
import com.jimbro.repository.FitnessLogRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {

    @Value("${groq.api.key}")
    private String apiKey;

    // Groq uses OpenAI-compatible API — fastest free inference available
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";

    @Autowired
    private ChatMessageRepository chatRepo;

    @Autowired
    private FitnessLogRepository logRepo;

    // ── Small helper: call the Groq API with a single user prompt ──────────────
    private String callGroq(String systemPrompt, String userMessage) {
        return callGroq(systemPrompt, userMessage, 4096);
    }

    private String callGroq(String systemPrompt, String userMessage, int maxTokens) {
        RestTemplate restTemplate = new RestTemplate();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", maxTokens);
        // Ask Groq to respond in valid JSON
        body.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String raw = restTemplate.postForObject(GROQ_URL, entity, String.class);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(raw);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Groq response: " + e.getMessage(), e);
        }
    }

    // ── Helper: strip markdown fences Groq may still add ─────────────────────
    private String stripFences(String text) {
        text = text.trim();
        if (text.startsWith("```json")) text = text.substring(7);
        else if (text.startsWith("```"))  text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        return text.trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  processChat — agentic JimBro chat
    // ─────────────────────────────────────────────────────────────────────────
    public ChatResponse processChat(ChatRequest request) {
        Long userId = request.getUserId();
        String userMessage = request.getMessage();
        String userContext = request.getUserContext() != null ? request.getUserContext() : "No user stats provided.";

        // 1. Save user message
        chatRepo.save(new ChatMessage(userId, "user", userMessage, LocalDateTime.now()));

        // 2. Build conversation history (last 10 msgs)
        List<ChatMessage> history = chatRepo.findByUserIdOrderByTimestampAsc(userId);
        StringBuilder historyPrompt = new StringBuilder();
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            historyPrompt.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
        }

        // 3. System prompt
        String systemPrompt = """
                You are 'JimBro', a highly supportive and knowledgeable gym partner AI.
                You speak encouragingly, celebrate PRs, and give practical fitness advice. Be concise and helpful.

                USER PROFILE CONTEXT:
                %s

                You have AGENCY. Parse the user's intent. If they log weight, complete a workout, or want to change their plan, output the appropriate action.

                YOU MUST RESPOND ONLY WITH A VALID JSON OBJECT. No markdown, no extra text outside the JSON.
                {
                   "message": "Your conversational reply here",
                   "actions": []
                }

                Valid Action Types and their EXACT value format:
                - LOG_WEIGHT: { "type": "LOG_WEIGHT", "value": 72.5 }
                - LOG_WORKOUT: { "type": "LOG_WORKOUT", "value": 0, "metadata": "Chest day - Bench Press 3x10" }
                - ADD_REMINDER: { "type": "ADD_REMINDER", "value": "Drink 3L of water" }
                - UPDATE_DIET: Only use when the user EXPLICITLY asks to change their diet plan. Value MUST be the COMPLETE updated diet JSON object:
                  { "type": "UPDATE_DIET", "value": { "dailyCalories": 2200, "macros": {"protein": 160, "carbs": 220, "fat": 70}, "meals": [{"name": "Breakfast", "time": "7:00 AM", "items": ["..."], "calories": 400, "protein": 30, "carbs": 50, "fat": 10}] } }
                - UPDATE_WORKOUT: Only use when the user EXPLICITLY asks to change their workout plan. Value MUST be the COMPLETE updated workout JSON object:
                  { "type": "UPDATE_WORKOUT", "value": { "days": [{"day": "Monday", "focus": "Chest & Triceps", "exercises": [{"name": "Bench Press", "sets": 4, "reps": "8-10", "rest": "90s"}]}, {"day": "Sunday", "focus": "Rest", "exercises": []}] } }

                CRITICAL RULES:
                - For UPDATE_DIET and UPDATE_WORKOUT, the 'value' MUST be a JSON OBJECT, NOT a string.
                - When updating diet/workout, include ALL meals/days. Keep unchanged parts identical.
                - Do NOT use UPDATE_DIET or UPDATE_WORKOUT unless explicitly requested.
                - If no action is needed, return: "actions": []
                """.formatted(userContext);

        String userTurn = "Chat History:\n" + historyPrompt + "\n\nLatest message: " + userMessage;

        // 4. Call Groq
        try {
            String aiText = stripFences(callGroq(systemPrompt, userTurn));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode aiJson = mapper.readTree(aiText);
            String messageToUser = aiJson.path("message").asText();

            List<String> actionsTaken = new ArrayList<>();
            Object rawActionsObj = null;

            // 5. Execute actions
            if (aiJson.has("actions") && aiJson.get("actions").isArray()) {
                JsonNode actionsArray = aiJson.get("actions");
                rawActionsObj = mapper.convertValue(actionsArray, Object.class);

                for (JsonNode action : actionsArray) {
                    String type = action.path("type").asText();
                    double value = action.path("value").asDouble(0.0);
                    String meta = action.path("metadata").asText("");

                    switch (type) {
                        case "LOG_WEIGHT" ->  { logRepo.save(new FitnessLog(userId, "WEIGHT", value, "", LocalDateTime.now())); actionsTaken.add("Logged weight: " + value + "kg"); }
                        case "LOG_WORKOUT" -> { logRepo.save(new FitnessLog(userId, "WORKOUT", 0.0, meta, LocalDateTime.now())); actionsTaken.add("Logged workout: " + meta); }
                        case "UPDATE_DIET" ->   actionsTaken.add("Updated diet plan.");
                        case "UPDATE_WORKOUT" -> actionsTaken.add("Updated workout plan.");
                        case "ADD_REMINDER" ->  actionsTaken.add("Added reminder.");
                    }
                }
            }

            // 6. Save JimBro reply
            chatRepo.save(new ChatMessage(userId, "model", messageToUser, LocalDateTime.now()));
            return new ChatResponse(messageToUser, actionsTaken, rawActionsObj);

        } catch (Exception e) {
            e.printStackTrace();
            return new ChatResponse("Oops, something went wrong on my end! Try again: " + e.getMessage(),
                    new ArrayList<>(), null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  generateInitialPlans — one-shot plan generation
    // ─────────────────────────────────────────────────────────────────────────
    public GeneratePlanResponse generateInitialPlans(GeneratePlanRequest req) {
        String dietNote = req.getDietPreference() != null && req.getDietPreference().equalsIgnoreCase("veg")
                ? "The person is VEGETARIAN. Do NOT include any meat, chicken, fish, or eggs."
                : "The person eats non-vegetarian food. You may include chicken, eggs, fish, and lean meats.";

        String systemPrompt = """
                You are a professional fitness coach and nutritionist.
                YOU MUST RESPOND ONLY WITH A VALID JSON OBJECT matching the EXACT structure requested. No markdown, no extra text.
                """;

        String userPrompt = """
                Create a complete 7-day Workout Plan and a full weekly Diet Plan for:
                Age: %s | Gender: %s | Height: %s cm | Weight: %s kg | Goal: %s | Fitness Level: %s
                Diet Note: %s

                Respond with EXACTLY this JSON structure:
                {
                  "workoutPlan": {
                    "days": [
                      {
                        "day": "Monday",
                        "focus": "Focus description",
                        "exercises": [
                          { "name": "Exercise Name", "sets": 3, "reps": "10", "rest": "60s" }
                        ]
                      }
                    ]
                  },
                  "dietPlan": {
                    "dailyCalories": 2000,
                    "macros": { "protein": 150, "carbs": 200, "fat": 60 },
                    "meals": [
                      {
                        "name": "Meal Name",
                        "time": "7:00 AM",
                        "items": ["Item A", "Item B"],
                        "calories": 400,
                        "protein": 30,
                        "carbs": 40,
                        "fat": 10
                      }
                    ]
                  }
                }

                RULES:
                - Provide EXACTLY 7 days (Monday through Sunday) in workoutPlan.
                - Include at least 1 rest day with empty exercises array.
                - Provide at least 5 meals in dietPlan.
                - STRICTLY ADHERE to the Diet Note: %s
                - Do NOT include food items forbidden in the Diet Note even if they appear in common templates.
                - Tailor exercises to the Goal and Fitness Level.
                """.formatted(req.getAge(), req.getGender(), req.getHeight(), req.getWeight(),
                req.getGoal(), req.getFitnessLevel(), dietNote, dietNote);

        try {
            String aiText = stripFences(callGroq(systemPrompt, userPrompt, 8192));
            System.out.println("[JimBro] Groq raw response (first 500 chars): " + aiText.substring(0, Math.min(500, aiText.length())));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode aiJson = mapper.readTree(aiText);

            JsonNode workoutNode = aiJson.path("workoutPlan");
            JsonNode dietNode    = aiJson.path("dietPlan");

            if (workoutNode.isMissingNode() || dietNode.isMissingNode()) {
                System.err.println("[JimBro] ERROR: Groq response is missing workoutPlan or dietPlan! Full response: " + aiText);
                throw new RuntimeException("Groq response missing expected fields");
            }

            String workoutJson = mapper.writeValueAsString(workoutNode);
            String dietJson    = mapper.writeValueAsString(dietNode);

            System.out.println("[JimBro] Generated workout days: " + workoutNode.path("days").size() + ", diet meals: " + dietNode.path("meals").size());
            return new GeneratePlanResponse(workoutJson, dietJson);
        } catch (Exception e) {
            System.err.println("[JimBro] FATAL: Plan generation failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate plans: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  reviseDiet — revise an existing diet plan
    // ─────────────────────────────────────────────────────────────────────────
    public GeneratePlanResponse reviseDiet(ReviseDietRequest req) {
        String dietPreference = req.getDietPreference() != null && req.getDietPreference().equalsIgnoreCase("veg")
                ? "VEGETARIAN — absolutely no meat, chicken, fish, or eggs."
                : "Non-vegetarian — includes chicken, eggs, fish, and lean meats.";

        String systemPrompt = """
                You are a professional nutritionist.
                YOU MUST RESPOND ONLY WITH A VALID JSON OBJECT matching the EXACT structure requested. No markdown, no extra text.
                """;

        String userPrompt = """
                Here is the user's current diet plan (JSON):
                %s

                The user wants these changes: "%s"

                Diet Preference: %s

                Revise the diet plan incorporating these changes and respond with EXACTLY this JSON structure:
                {
                  "dietPlan": {
                    "dailyCalories": 2000,
                    "macros": { "protein": 150, "carbs": 200, "fat": 60 },
                    "meals": [
                      {
                        "name": "Meal Name",
                        "time": "7:00 AM",
                        "items": ["Item A", "Item B"],
                        "calories": 400,
                        "protein": 30,
                        "carbs": 40,
                        "fat": 10
                      }
                    ]
                  }
                }
                """.formatted(req.getCurrentDiet(), req.getChangeRequest(), dietPreference);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String aiText = stripFences(callGroq(systemPrompt, userPrompt, 6000));
            JsonNode aiJson = mapper.readTree(aiText);
            JsonNode dietNode = aiJson.path("dietPlan");
            String dietJson = mapper.writeValueAsString(dietNode.isMissingNode() ? aiJson : dietNode);
            return new GeneratePlanResponse(null, dietJson);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to revise diet: " + e.getMessage(), e);
        }
    }
}
