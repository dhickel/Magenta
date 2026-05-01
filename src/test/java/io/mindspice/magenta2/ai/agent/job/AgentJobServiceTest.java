package io.mindspice.magenta2.ai.agent.job;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AgentJobServiceTest {

    @Test
    void cleansBlankAndOverlongTitles() {
        AgentJobService service = service(jdbcTemplate(), new SyncTaskExecutor());

        assertThat(service.cleanTitle("   ")).isNull();
        assertThat(service.cleanTitle("<think>notes</think>\n\"Discuss reminder follow-up\""))
            .isEqualTo("Discuss reminder follow-up");
        assertThat(service.cleanTitle("This title is intentionally far too long for a compact sidebar label and should be shortened"))
            .hasSizeLessThanOrEqualTo(AgentJobService.MAX_TITLE_LENGTH)
            .doesNotEndWith(" ");
    }

    @Test
    void titleJobUpdatesConversationMetadata() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AgentJobRepository jobRepository = new AgentJobRepository(jdbcTemplate);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        AgentJobService service = new FixedTitleAgentJobService(jobRepository, metadataRepository, new SyncTaskExecutor(), "Useful Title");

        service.submitConversationTitle("conversation-1", "qwen3", "Please help me plan reminders");

        assertThat(metadataRepository.findTitle("conversation-1")).contains("Useful Title");
        AgentJob job = jobRepository.findAll().getFirst();
        assertThat(job.status()).isEqualTo(AgentJobStatus.SUCCEEDED);
        assertThat(job.selectedModel()).isEqualTo("qwen3");
        assertThat(job.resultJson()).contains("Useful Title");
    }

    @Test
    void titleJobUsesConfiguredSummeryModel() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AgentJobRepository jobRepository = new AgentJobRepository(jdbcTemplate);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        FixedTitleAgentJobService service = new FixedTitleAgentJobService(
            jobRepository,
            metadataRepository,
            aiConfig(),
            new SyncTaskExecutor(),
            "Useful Title"
        );

        service.submitConversationTitle("conversation-1", "chat-remote", "Please help me plan reminders");

        assertThat(service.lastSelectedModel).isEqualTo("summary-remote");
        assertThat(jobRepository.findAll().getFirst().selectedModel()).isEqualTo("summary-remote");
    }

    @Test
    void boundedExecutorRunsAtMostTwoJobsAtOnce() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AgentJobRepository jobRepository = new AgentJobRepository(jdbcTemplate);
        ChatSessionMetadataRepository metadataRepository = new ChatSessionMetadataRepository(jdbcTemplate);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();
        try {
            BlockingTitleAgentJobService service = new BlockingTitleAgentJobService(
                jobRepository,
                metadataRepository,
                executor
            );

            for (int i = 0; i < 4; i++) {
                service.submitConversationTitle("conversation-" + i, "qwen3", "message " + i);
            }

            assertThat(service.twoJobsStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.maxRunning.get()).isEqualTo(2);
            service.release.countDown();
            executor.getThreadPoolExecutor().shutdown();
            assertThat(executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.maxRunning.get()).isEqualTo(2);
        } finally {
            executor.shutdown();
        }
    }

    private AgentJobService service(JdbcTemplate jdbcTemplate, org.springframework.core.task.TaskExecutor executor) {
        return new AgentJobService(
            new AgentJobRepository(jdbcTemplate),
            new ChatSessionMetadataRepository(jdbcTemplate),
            null,
            new ObjectMapper(),
            executor
        );
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            "magenta",
            "summary-model",
            10,
            null,
            Map.of(
                "chat-model", new ModelConfig("chat-remote", "http://localhost:11434", EndpointType.OLLAMA, 8192),
                "summary-model", new ModelConfig("summary-remote", "http://localhost:11434", EndpointType.OLLAMA, 8192)
            ),
            Map.of("magenta", new AgentConfig("chat-model", "prompt", List.of()))
        );
    }

    private static class FixedTitleAgentJobService extends AgentJobService {
        private final String title;
        private String lastSelectedModel;

        FixedTitleAgentJobService(
            AgentJobRepository jobRepository,
            ChatSessionMetadataRepository metadataRepository,
            AiConfig aiConfig,
            org.springframework.core.task.TaskExecutor executor,
            String title
        ) {
            super(jobRepository, metadataRepository, null, aiConfig, new ObjectMapper(), executor);
            this.title = title;
        }

        FixedTitleAgentJobService(
            AgentJobRepository jobRepository,
            ChatSessionMetadataRepository metadataRepository,
            org.springframework.core.task.TaskExecutor executor,
            String title
        ) {
            this(jobRepository, metadataRepository, null, executor, title);
        }

        @Override
        String generateTitle(String selectedModel, String firstUserMessage) {
            lastSelectedModel = selectedModel;
            return title;
        }
    }

    private static class BlockingTitleAgentJobService extends AgentJobService {
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger maxRunning = new AtomicInteger();
        private final CountDownLatch twoJobsStarted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingTitleAgentJobService(
            AgentJobRepository jobRepository,
            ChatSessionMetadataRepository metadataRepository,
            org.springframework.core.task.TaskExecutor executor
        ) {
            super(jobRepository, metadataRepository, null, new ObjectMapper(), executor);
        }

        @Override
        String generateTitle(String selectedModel, String firstUserMessage) {
            int current = running.incrementAndGet();
            maxRunning.accumulateAndGet(current, Math::max);
            twoJobsStarted.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                return "Title";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                running.decrementAndGet();
            }
        }
    }
}
