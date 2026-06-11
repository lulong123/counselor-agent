package com.counselor.agent.repository;

import com.counselor.agent.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByThreadIdOrderBySeqAsc(String threadId);

    List<Message> findByThreadIdOrderBySeqDesc(String threadId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM Message m WHERE m.threadId = :threadId")
    int findMaxSeqByThreadId(@Param("threadId") String threadId);

    List<Message> findByThreadIdAndSeqLessThanOrderBySeqAsc(
            String threadId, int beforeSeq, Pageable pageable);

    int countByThreadId(String threadId);

    void deleteByThreadId(String threadId);
}
