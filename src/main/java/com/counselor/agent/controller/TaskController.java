package com.counselor.agent.controller;

import com.counselor.agent.filter.TeacherFilter;
import com.counselor.agent.model.Task;
import com.counselor.agent.model.RunStatus;
import com.counselor.agent.repository.TaskRepository;
import com.counselor.agent.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;
    private final TaskRepository taskRepository;

    public TaskController(TaskService taskService, TaskRepository taskRepository) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createTask(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }

        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);

        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setTeacherId(teacherId);
        task.setContent(content.trim());
        task.setStatus(RunStatus.pending);
        taskRepository.save(task);

        log.info("Task created: id={}, teacherId={}, content={}", task.getId(), teacherId, content);

        SseEmitter emitter = new SseEmitter(300_000L);

        try {
            emitter.send(SseEmitter.event()
                    .name("stage")
                    .data(Map.of("stage", "RECEIVED", "taskId", task.getId())));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // 异步处理（不阻塞 Controller 线程）
        new Thread(() -> taskService.processTask(task, emitter)).start();

        return emitter;
    }

    @GetMapping
    public List<Task> listTasks(HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return taskService.listTasks(teacherId);
    }

    @GetMapping("/{id}")
    public Task getTask(@PathVariable String id, HttpServletRequest request) {
        String teacherId = (String) request.getAttribute(TeacherFilter.TEACHER_ID_ATTR);
        return taskRepository.findById(id)
                .filter(t -> t.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
    }
}
