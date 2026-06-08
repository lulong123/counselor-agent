package com.counselor.agent.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRouterTest {

    private AgentRouter router;

    @BeforeEach
    void setUp() {
        router = new AgentRouter(List.of(
                new TestAgent("mingli", "明理", "思政引领", List.of("思政", "班会")),
                new TestAgent("duxue", "笃学", "学风建设", List.of("挂科", "成绩")),
                new TestAgent("xinqing", "心晴", "心理支持", List.of("心理", "焦虑"))
        ));
    }

    @Test
    void shouldDispatchToCorrectAgent() {
        Optional<SubAgent> agent = router.dispatch("mingli");
        assertThat(agent).isPresent();
        assertThat(agent.get().getName()).isEqualTo("明理");
    }

    @Test
    void shouldReturnEmptyForUnknownAgent() {
        Optional<SubAgent> agent = router.dispatch("unknown");
        assertThat(agent).isEmpty();
    }

    @Test
    void shouldReturnAllAgents() {
        List<SubAgent> agents = router.allAgents();
        assertThat(agents).hasSize(3);
    }

    @Test
    void allNineAgentIdsShouldBeRoutable() {
        AgentRouter fullRouter = new AgentRouter(List.of(
                new TestAgent("mingli", "明理", "思政引领", List.of("思政")),
                new TestAgent("tongxin", "同心", "党团班级", List.of("入党")),
                new TestAgent("duxue", "笃学", "学风建设", List.of("挂科")),
                new TestAgent("youxu", "有序", "日常事务", List.of("材料")),
                new TestAgent("xinqing", "心晴", "心理支持", List.of("心理")),
                new TestAgent("yunlan", "云澜", "网络思政", List.of("舆情")),
                new TestAgent("shouwang", "守望", "危机应对", List.of("危机")),
                new TestAgent("qihang", "启航", "就业创业", List.of("就业")),
                new TestAgent("yanxing", "研行", "实践研究", List.of("调研"))
        ));

        List<String> expectedIds = List.of(
                "mingli", "tongxin", "duxue", "youxu", "xinqing",
                "yunlan", "shouwang", "qihang", "yanxing"
        );

        for (String id : expectedIds) {
            assertThat(fullRouter.dispatch(id))
                    .as("Agent '%s' should be routable", id)
                    .isPresent();
        }
    }

    private record TestAgent(
            String id, String name, String duty,
            List<String> keywords
    ) implements SubAgent {
        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getDuty() { return duty; }
        @Override public String getSystemPrompt() { return "Test prompt for " + name; }
        @Override public List<String> getKeywords() { return keywords; }
        @Override public Flux<String> execute(String userInput) {
            return Flux.just("test response from " + name);
        }
    }
}
