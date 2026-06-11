package com.counselor.agent.controller;

import com.counselor.agent.filter.TeacherFilter;
import com.counselor.agent.model.Thread;
import com.counselor.agent.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/threads")
public class ThreadController {

    private static final Logger log = LoggerFactory.getLogger(ThreadController.class);
    private final ThreadService threadService;

    public ThreadController(ThreadService threadService) {
        this.threadService = threadService;
    }

    @PostMapping
    public Thread createThread(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return threadService.createThread(teacherId, body.get("title"));
    }

    @GetMapping
    public List<Thread> listThreads(HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return threadService.listThreads(teacherId);
    }

    @GetMapping("/{id}")
    public Thread getThread(@PathVariable String id, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return threadService.getThread(id)
                .filter(t -> t.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
    }

    @PatchMapping("/{id}")
    public Thread updateThread(@PathVariable String id, @RequestBody Map<String, String> body) {
        threadService.updateTitle(id, body.get("title"));
        return threadService.getThread(id).orElseThrow();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteThread(@PathVariable String id, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        threadService.getThread(id).filter(t -> t.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + id));
        threadService.deleteThread(id);
        return Map.of("success", true);
    }
}
