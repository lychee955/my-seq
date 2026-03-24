package cn.lz.seq.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SeqDao.
 * Tests SQL building, key retrieval, and exception propagation.
 */
@ExtendWith(MockitoExtension.class)
class SeqDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SeqDao seqDao;

    @Test
    void testGetSeq_ExecutesCorrectSql() {
        String tableName = "test_table";
        BigInteger expectedSeq = BigInteger.valueOf(123L);

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    // Simulate GeneratedKeyHolder with our expected key
                    keyHolder.getKeyList().add(Collections.singletonMap("GENERATED_KEY", expectedSeq));
                    return 1;
                });

        BigInteger result = seqDao.getSeq(tableName);

        assertThat(result).isEqualTo(expectedSeq);
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void testGetSeq_SqlContainsCorrectTableName() {
        String tableName = "video_seq";

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    keyHolder.getKeyList().add(Collections.singletonMap("GENERATED_KEY", BigInteger.ONE));
                    return 1;
                });

        seqDao.getSeq(tableName);

        // Verify the PreparedStatementCreator is called
        verify(jdbcTemplate).update(argThat(new PreparedStatementCreatorMatcher(tableName)), any(KeyHolder.class));
    }

    @Test
    void testGetSeq_PropagatesException() {
        String tableName = "test_table";
        RuntimeException expectedException = new RuntimeException("Database error");

        doThrow(expectedException)
                .when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        assertThatThrownBy(() -> seqDao.getSeq(tableName))
                .isEqualTo(expectedException);
    }

    @Test
    void testGetSeq_ReturnsCorrectBigInteger() {
        String tableName = "test_table";
        BigInteger largeSeq = new BigInteger("9876543210987654321");

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder keyHolder = invocation.getArgument(1);
                    keyHolder.getKeyList().add(Collections.singletonMap("GENERATED_KEY", largeSeq));
                    return 1;
                });

        BigInteger result = seqDao.getSeq(tableName);

        assertThat(result).isEqualTo(largeSeq);
    }

    /**
     * Matcher to verify the SQL contains the correct table name.
     */
    private static class PreparedStatementCreatorMatcher implements ArgumentMatcher<PreparedStatementCreator> {
        private final String expectedTableName;

        PreparedStatementCreatorMatcher(String expectedTableName) {
            this.expectedTableName = expectedTableName;
        }

        @Override
        public boolean matches(PreparedStatementCreator psc) {
            try {
                // We can't easily get the SQL from the anonymous inner class,
                // but we can verify it creates a PreparedStatement
                Connection mockConnection = mock(Connection.class);
                PreparedStatement mockPs = mock(PreparedStatement.class);
                when(mockConnection.prepareStatement(any(String.class), any(String[].class))).thenReturn(mockPs);

                psc.createPreparedStatement(mockConnection);

                // Verify the SQL was called with the right table name pattern
                String expectedSql = "replace into " + expectedTableName + " (stub) values ('0')";
                verify(mockConnection).prepareStatement(eq(expectedSql), any(String[].class));
                return true;
            } catch (SQLException e) {
                return false;
            }
        }
    }
}
