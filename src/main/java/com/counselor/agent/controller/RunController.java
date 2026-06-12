package com.counselor.agent.controller;

import com.counselor.agent.filter.TeacherFilter;
import com.counselor.agent.model.Message;
import com.counselor.agent.model.Task;
import com.counselor.agent.repository.TaskRepository;
import com.counselor.agent.service.RunService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/threads/{threadId}")
public class RunController {

    private static final Logger log = LoggerFactory.getLogger(RunController.class);

    private final RunService runService;
    private final TaskRepository taskRepository;

    public RunController(RunService runService, TaskRepository taskRepository) {
        this.runService = runService;
        this.taskRepository = taskRepository;
    }

    @PostMapping(value = "/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String threadId,
                                @RequestBody Map<String, String> body,
                                HttpServletRequest request) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        boolean deepThinking = "true".equals(body.get("deepThinking"));

        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);

        SseEmitter emitter = new SseEmitter(300_000L);

        // 异步处理，不阻塞 Controller 线程
        new Thread(() -> {
            try {
                runService.processRun(threadId, teacherId, content.trim(), deepThinking, emitter);
            } catch (Exception e) {
                log.error("Run failed for thread {}: {}", threadId, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data("{\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}"));
                } catch (Exception ignored) { /* emitter already closed */ }
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    @GetMapping("/runs")
    public List<Task> listRuns(@PathVariable String threadId, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return taskRepository.findByThreadIdOrderByCreatedAtDesc(threadId);
    }

    @GetMapping("/messages")
    public List<Message> listMessages(@PathVariable String threadId,
                                      @RequestParam(defaultValue = "0") int beforeSeq,
                                      @RequestParam(defaultValue = "50") int limit) {
        return runService.getMessages(threadId, beforeSeq, limit);
    }
}
