package com.counselor.agent.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_thread_seq", columnList = "thread_id, seq")
})
public class Message {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "thread_id", length = 36, nullable = false)
    private String threadId;

    @Column(name = "teacher_id", length = 64, nullable = false)
    private String teacherId;

    @Column(length = 16, nullable = false)
    private String role;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "agent_id", length = 32)
    private String agentId;

    @Column(length = 16)
    private String risk;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String thinking;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @Column(nullable = false)
    private Integer seq = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }

    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }

    public Integer getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
