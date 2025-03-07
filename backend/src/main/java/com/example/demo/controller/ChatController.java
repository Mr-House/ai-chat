package com.example.demo.controller;

import com.example.demo.model.Message;
import com.example.demo.service.DeepSeekService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private DeepSeekService deepSeekService;

    @PostMapping("/send")
    public Message sendMessage(@RequestBody Message message) {
        return deepSeekService.generateResponse(message);
    }
    
    @PostMapping("/stream")
    public SseEmitter streamMessage(@RequestBody Message message) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        emitter.onCompletion(() -> System.out.println("SSE completed"));
        emitter.onTimeout(() -> System.out.println("SSE timeout"));
        emitter.onError(e -> System.out.println("SSE error: " + e.getMessage()));
        
        // 异步处理流式响应
        new Thread(() -> {
            try {
                deepSeekService.generateStreamResponse(message, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        
        return emitter;
    }
}