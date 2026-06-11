package com.counselor.agent.repository;

import com.counselor.agent.model.Thread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThreadRepository extends JpaRepository<Thread, String> {

    List<Thread> findByTeacherIdOrderByUpdatedAtDesc(String teacherId);

    List<Thread> findByTeacherIdAndTitleContainingOrderByUpdatedAtDesc(String teacherId, String keyword);
}
