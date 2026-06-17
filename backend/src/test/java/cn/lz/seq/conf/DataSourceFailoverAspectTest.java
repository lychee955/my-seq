package cn.lz.seq.conf;

import cn.lz.seq.dao.DynamicSwitch;
import cn.lz.seq.service.RandomRoutingStrategy;
import cn.lz.seq.service.RoutingStrategyFactory;
import cn.lz.seq.zk.ZNodeWatcherService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private ZNodeWatcherService zNodeWatcherService;

    @InjectMocks
    private DataSourceFailoverAspect aspect;

    @BeforeEach
    void setUp() {
        lenient().when(zNodeWatcherService.getCurStrategy()).thenReturn("random");
        lenient().when(routingStrategyFactory.getRoutingStrategy("random")).thenReturn(randomRoutingStrategy);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getDeclaringTypeName()).thenReturn("cn.lz.seq.dao.SeqDao");
        lenient().when(signature.getName()).thenReturn("getSeq");
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @Test
    void testNormalFlow_Success() throws Throwable {
        Object expectedResult = 123L;
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenReturn(expectedResult);

        Object result = aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        assertThat(result).isEqualTo(expectedResult);
        verify(randomRoutingStrategy).selectDb();
        verify(joinPoint).proceed();
        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }

    @Test
    void testConnectionException_FailoverAndRetry() throws Throwable {
        String firstDb = "db1";
        String secondDb = "db2";
        Object expectedResult = 456L;

        when(randomRoutingStrategy.selectDb()).thenReturn(firstDb);
        when(dynamicDataSource.selectAliveDataSource(firstDb)).thenReturn(secondDb);
        when(joinPoint.proceed())
                .thenThrow(new CannotGetJdbcConnectionException("Connection failed"))
                .thenReturn(expectedResult);

        Object result = aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        assertThat(result).isEqualTo(expectedResult);
        verify(joinPoint, times(2)).proceed();
        verify(dynamicDataSource).markAsFault(firstDb);
        verify(dynamicDataSource).selectAliveDataSource(firstDb);
    }

    @Test
    void testNonConnectionException_NoFailover() throws Throwable {
        String db = "db1";
        RuntimeException nonConnectionException = new RuntimeException("SQL error");

        when(randomRoutingStrategy.selectDb()).thenReturn(db);
        when(joinPoint.proceed()).thenThrow(nonConnectionException);

        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch))
                .isEqualTo(nonConnectionException);

        verify(joinPoint).proceed();
        verify(dynamicDataSource, never()).markAsFault(anyString());
    }

    @Test
    void testSecondFailure_ThrowsOriginalException() throws Throwable {
        String firstDb = "db1";
        String secondDb = "db2";
        CannotGetJdbcConnectionException secondException = new CannotGetJdbcConnectionException("Second failed");

        when(randomRoutingStrategy.selectDb()).thenReturn(firstDb);
        when(dynamicDataSource.selectAliveDataSource(firstDb)).thenReturn(secondDb);
        when(joinPoint.proceed())
                .thenThrow(new CannotGetJdbcConnectionException("First failed"))
                .thenThrow(secondException);

        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch))
                .isEqualTo(secondException);

        verify(joinPoint, times(2)).proceed();
        verify(dynamicDataSource).markAsFault(firstDb);
    }

    @Test
    void testFinallyBlock_ClearsContext() throws Throwable {
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenReturn("result");

        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch);

        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }

    @Test
    void testFinallyBlock_ClearsContextEvenOnException() throws Throwable {
        when(randomRoutingStrategy.selectDb()).thenReturn("db1");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Error"));

        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        assertThatThrownBy(() -> aspect.afterThrowingDataAccessOperationException(joinPoint, dynamicSwitch));

        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isNull();
    }
}
