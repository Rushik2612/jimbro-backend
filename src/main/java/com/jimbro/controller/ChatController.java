package com.jimbro.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.jimbro.dto.ChatRequest;
import com.jimbro.dto.ChatResponse;
import com.jimbro.dto.GeneratePlanRequest;
import com.jimbro.dto.GeneratePlanResponse;
import com.jimbro.dto.ReviseDietRequest;
import com.jimbro.service.ChatService;
import com.jimbro.entity.ChatMessage;
import com.jimbro.entity.FitnessLog;
import com.jimbro.repository.ChatMessageRepository;
import com.jimbro.repository.FitnessLogRepository;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatRepo;

    @Autowired
    private FitnessLogRepository logRepo;

    @PostMapping("/send")
    public ChatResponse sendMessage(@RequestBody ChatRequest request) {
        return chatService.processChat(request);
    }

    @PostMapping("/generate-plans")
    public GeneratePlanResponse generatePlans(@RequestBody GeneratePlanRequest request) {
        return chatService.generateInitialPlans(request);
    }

    @PostMapping("/revise-diet")
    public GeneratePlanResponse reviseDiet(@RequestBody ReviseDietRequest request) {
        return chatService.reviseDiet(request);
    }

    @GetMapping("/history/{userId}")
    public List<ChatMessage> getHistory(@PathVariable Long userId) {
        return chatRepo.findByUserIdOrderByTimestampAsc(userId);
    }

    @GetMapping("/logs/{userId}")
    public List<FitnessLog> getLogs(@PathVariable Long userId) {
        return logRepo.findByUserIdOrderByTimestampDesc(userId);
    }
}
