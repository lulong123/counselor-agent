package com.counselor.agent.repository;

import com.counselor.agent.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
}
