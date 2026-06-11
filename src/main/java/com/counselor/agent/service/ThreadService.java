package com.counselor.agent.service;

import com.counselor.agent.model.Thread;
import com.counselor.agent.model.ThreadStatus;
import com.counselor.agent.repository.MessageRepository;
import com.counselor.agent.repository.TaskRepository;
import com.counselor.agent.repository.ThreadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ThreadService {

    private static final Logger log = LoggerFactory.getLogger(ThreadService.class);
    private final ThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;

    public ThreadService(ThreadRepository threadRepository,
                         MessageRepository messageRepository,
                         TaskRepository taskRepository) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
    }

    public Thread createThread(String teacherId, String title) {
        Thread t = new Thread();
        t.setId(UUID.randomUUID().toString());
        t.setTeacherId(teacherId);
        t.setTitle(title != null && !title.isBlank() ? title.trim() : null);
        t.setStatus(ThreadStatus.idle);
        threadRepository.save(t);
        log.info("Thread created: id={}, teacherId={}", t.getId(), teacherId);
        return t;
    }

    public List<Thread> listThreads(String teacherId) {
        return threadRepository.findByTeacherIdOrderByUpdatedAtDesc(teacherId);
    }

    public Optional<Thread> getThread(String id) {
        return threadRepository.findById(id);
    }

    public void updateTitle(String id, String title) {
        threadRepository.findById(id).ifPresent(t -> {
            t.setTitle(title != null ? title.trim() : null);
            t.setUpdatedAt(Instant.now());
            threadRepository.save(t);
        });
    }

    @Transactional
    public void deleteThread(String id) {
        messageRepository.deleteByThreadId(id);
        taskRepository.deleteByThreadId(id);
        threadRepository.deleteById(id);
        log.info("Thread deleted: id={}", id);
    }
}
