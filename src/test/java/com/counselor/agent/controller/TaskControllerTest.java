package com.counselor.agent.controller;

import com.counselor.agent.model.Task;
import com.counselor.agent.model.RunStatus;
import com.counselor.agent.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    void shouldRejectRequestWithoutTeacherId() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType("application/json")
                        .content("{\"content\":\"测试任务\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListTasksForTeacher() throws Exception {
        Task taskA = createTask("teacher-A", "任务A");
        Task taskB = createTask("teacher-B", "任务B");
        taskRepository.save(taskA);
        taskRepository.save(taskB);

        mockMvc.perform(get("/api/tasks")
                        .header("X-Teacher-Id", "teacher-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherId").value("teacher-A"));
    }

    @Test
    void shouldReturnTaskOnlyToOwner() throws Exception {
        Task task = createTask("teacher-A", "我的任务");
        taskRepository.save(task);

        mockMvc.perform(get("/api/tasks/" + task.getId())
                        .header("X-Teacher-Id", "teacher-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));

        mockMvc.perform(get("/api/tasks/" + task.getId())
                        .header("X-Teacher-Id", "teacher-B"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRejectEmptyContent() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .header("X-Teacher-Id", "teacher-A")
                        .contentType("application/json")
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private Task createTask(String teacherId, String content) {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setTeacherId(teacherId);
        task.setContent(content);
        task.setStatus(RunStatus.pending);
        return task;
    }
}
