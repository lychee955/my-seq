package cn.lz.seq.conf;

import cn.lz.seq.dao.DynamicSwitch;
import cn.lz.seq.service.RandomRoutingStrategy;
import cn.lz.seq.service.RoutingStrategyFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DataSourceFailoverAspect.
 * Tests normal flow, connection failover, non-connection exception handling, and retry logic.
 */
@ExtendWith(MockitoExtension.class)
class DataSourceFailoverAspectTest {

    @Mock
    private RoutingStrategyFactory routingStrategyFactory;

    @Mock
    private DynamicDataSource dynamicDataSource;

    @Mock
    private RandomRoutingStrategy randomRoutingStrategy;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @Mock
    private DynamicSwitch dynamicSwitch;

    @InjectMocks
    private DataSourceFailoverAspect aspect;

    private Map<String, Long> faultDataSourceMap;

    @BeforeEach
    void setUp() {
        faultDataSourceMap = new ConcurrentHashMap<>();
        lenient().when(dynamicDataSource.getFaultDataSourceMap()).thenReturn(faultDataSourceMap);
        when(routingStrategyFactory.getRoutingStrategy(anyString())).thenReturn(randomRoutingStrategy);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("cn.lz.seq.dao.SeqDao");
        when(signature.getName()).thenReturn("getSeq");
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @Test
    void testNormalFlow_Success() throws Throwable {
        // Setup
        Object expectedResult = 123L;
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Execute
        Object result = aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        // Verify
        assertThat(result).isEqualTo(expectedResult);
        verify(randomRoutingStrategy).selectDb();
        verify(joinPoint).proceed();
        // Verify context was cleared
        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }

    @Test
    void testConnectionException_FailoverAndRetry() throws Throwable {
        // Setup
        String firstDb = "db1";
        String secondDb = "db2";
        Object expectedResult = 456L;

        DynamicDataSourceContextHolder.setDataSourceNo(firstDb);
        when(randomRoutingStrategy.selectDb()).thenReturn(firstDb).thenReturn(secondDb);
        when(joinPoint.proceed())
                .thenThrow(new CannotGetJdbcConnectionException("Connection failed"))
                .thenReturn(expectedResult);

        // Execute
        Object result = aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        // Verify
        assertThat(result).isEqualTo(expectedResult);
        verify(joinPoint, times(2)).proceed();
        verify(randomRoutingStrategy, times(2)).selectDb();
        verify(dynamicDataSource).handleFaultDataSource();
        // Verify fault was marked
        assertThat(faultDataSourceMap).containsKey(firstDb);
    }

    @Test
    void testNonConnectionException_NoFailover() throws Throwable {
        // Setup
        String db = "db1";
        RuntimeException nonConnectionException = new RuntimeException("SQL error");

        when(randomRoutingStrategy.selectDb()).thenReturn(db);
        when(joinPoint.proceed()).thenThrow(nonConnectionException);

        // Execute & Verify
        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch))
                .isEqualTo(nonConnectionException);

        // Verify only one attempt, no failover
        verify(joinPoint).proceed();
        verify(dynamicDataSource, never()).handleFaultDataSource();
        assertThat(faultDataSourceMap).isEmpty();
    }

    @Test
    void testSecondFailure_ThrowsOriginalException() throws Throwable {
        // Setup
        String firstDb = "db1";
        String secondDb = "db2";
        CannotGetJdbcConnectionException firstException = new CannotGetJdbcConnectionException("First failed");
        CannotGetJdbcConnectionException secondException = new CannotGetJdbcConnectionException("Second failed");

        DynamicDataSourceContextHolder.setDataSourceNo(firstDb);
        when(randomRoutingStrategy.selectDb()).thenReturn(firstDb).thenReturn(secondDb);
        when(joinPoint.proceed())
                .thenThrow(firstException)
                .thenThrow(secondException);

        // Execute & Verify
        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch))
                .isEqualTo(secondException);

        // Verify two attempts
        verify(joinPoint, times(2)).proceed();
        assertThat(faultDataSourceMap).containsKey(firstDb);
    }

    @Test
    void testFinallyBlock_ClearsContext() throws Throwable {
        // Setup
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenReturn("result");

        // Set some context before
        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        // Execute
        aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        // Verify context was cleared in finally block
        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }

    @Test
    void testFinallyBlock_ClearsContextEvenOnException() throws Throwable {
        // Setup
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

        // Set some context before
        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        // Execute
        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch));

        // Verify context was cleared in finally block
        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }
}
