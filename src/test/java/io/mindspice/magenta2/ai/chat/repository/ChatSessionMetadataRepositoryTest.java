package io.mindspice.magenta2.ai.chat.repository;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSessionMetadataRepositoryTest {

    @Test
    void normalFavoriteAndArchiveFlagsStillWork() {
        ChatSessionMetadataRepository repository = new ChatSessionMetadataRepository(jdbcTemplate());

        repository.setFavorite("conv-1", true);
        repository.setArchived("conv-1", true);

        assertThat(repository.isFavorite("conv-1")).isTrue();
        assertThat(repository.isArchived("conv-1")).isTrue();

        repository.setFavorite("conv-1", false);

        assertThat(repository.isFavorite("conv-1")).isFalse();
        assertThat(repository.isArchived("conv-1")).isTrue();
    }

    @Test
    void privateFlagHelpersRejectUnsafeIdentifierPayloads() throws Throwable {
        ChatSessionMetadataRepository repository = new ChatSessionMetadataRepository(jdbcTemplate());

        assertThatThrownBy(() -> invokePrivate(
            repository,
            "booleanValue",
            new Class<?>[] {String.class, String.class},
            "conv-1",
            "favorite from ai_chat_session_metadata; drop table ai_chat_session_metadata; --"
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported metadata flag column");

        assertThatThrownBy(() -> invokePrivate(
            repository,
            "updateFlag",
            new Class<?>[] {String.class, String.class, boolean.class},
            "conv-1",
            "archived = 1 where 1=1 --",
            true
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported metadata flag column");
    }

    private Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
        throws Throwable {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        return new JdbcTemplate(dataSource);
    }
}
