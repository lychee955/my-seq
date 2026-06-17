package cn.lz.seq.service;

import cn.lz.seq.conf.DynamicDataSource;
import cn.lz.seq.conf.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RandomRoutingStrategyTest {

    @Mock
    private DynamicDataSource dynamicDataSource;

    @InjectMocks
    private RandomRoutingStrategy randomRoutingStrategy;

    @BeforeEach
    void setUp() {
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @Test
    void testSelectDb_WithMultipleDataSources_ReturnsOneOfThem() {
        lenient().when(dynamicDataSource.selectAliveDataSource(isNull())).thenReturn("db1");

        String selected = randomRoutingStrategy.selectDb();

        assertThat(selected).isEqualTo("db1");
    }

    @Test
    void testSelectDb_SetsContextHolderCorrectly() {
        lenient().when(dynamicDataSource.selectAliveDataSource(isNull())).thenReturn("db2");

        String selected = randomRoutingStrategy.selectDb();

        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isEqualTo(selected);
    }

    @Test
    void testSelectDb_WithSingleDataSource_AlwaysReturnsIt() {
        lenient().when(dynamicDataSource.selectAliveDataSource(isNull())).thenReturn("only-db");

        for (int i = 0; i < 10; i++) {
            String selected = randomRoutingStrategy.selectDb();
            assertThat(selected).isEqualTo("only-db");
            DynamicDataSourceContextHolder.clearDataSourceNos();
        }
    }

    @Test
    void testSelectDb_DelegatesToDynamicDataSource() {
        lenient().when(dynamicDataSource.selectAliveDataSource(isNull()))
                .thenReturn("db1")
                .thenReturn("db2");

        Map<String, Integer> selectionCount = new ConcurrentHashMap<>();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            String selected = randomRoutingStrategy.selectDb();
            selectionCount.merge(selected, 1, Integer::sum);
            DynamicDataSourceContextHolder.clearDataSourceNos();
        }

        assertThat(selectionCount).containsKey("db1");
        assertThat(selectionCount).containsKey("db2");
    }

    @Test
    void testSelectDb_WithThreeDataSources_ReturnsOneOfThem() {
        lenient().when(dynamicDataSource.selectAliveDataSource(isNull())).thenReturn("db3");

        String selected = randomRoutingStrategy.selectDb();

        assertThat(selected).isEqualTo("db3");
        assertThat(DynamicDataSourceContextHolder.getDataSourceNo()).isEqualTo(selected);
    }
}
