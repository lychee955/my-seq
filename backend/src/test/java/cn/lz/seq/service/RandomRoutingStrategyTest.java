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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RandomRoutingStrategy.
 * Tests random selection, context setting, and multi-data source scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RandomRoutingStrategyTest {

    @Mock
    private DynamicDataSource dynamicDataSource;

    @InjectMocks
    private RandomRoutingStrategy randomRoutingStrategy;

    @BeforeEach
    void setUp() {
        DynamicDataSourceContextHolder.clearDateSourceNos();
    }

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clearDateSourceNos();
    }

    @Test
    void testSelectDb_WithMultipleDataSources_ReturnsOneOfThem() {
        CopyOnWriteArrayList<String> dataSources = new CopyOnWriteArrayList<>();
        dataSources.add("db1");
        dataSources.add("db2");
        when(dynamicDataSource.getAllAliveDataSourceKeys()).thenReturn(dataSources);

        String selected = randomRoutingStrategy.selectDb();

        assertThat(selected).isIn(dataSources);
    }

    @Test
    void testSelectDb_SetsContextHolderCorrectly() {
        CopyOnWriteArrayList<String> dataSources = new CopyOnWriteArrayList<>();
        dataSources.add("db1");
        dataSources.add("db2");
        when(dynamicDataSource.getAllAliveDataSourceKeys()).thenReturn(dataSources);

        String selected = randomRoutingStrategy.selectDb();

        assertThat(DynamicDataSourceContextHolder.getDateSourceNo()).isEqualTo(selected);
    }

    @Test
    void testSelectDb_WithSingleDataSource_AlwaysReturnsIt() {
        CopyOnWriteArrayList<String> singleDataSource = new CopyOnWriteArrayList<>();
        singleDataSource.add("only-db");
        when(dynamicDataSource.getAllAliveDataSourceKeys()).thenReturn(singleDataSource);

        for (int i = 0; i < 10; i++) {
            String selected = randomRoutingStrategy.selectDb();
            assertThat(selected).isEqualTo("only-db");
        }
    }

    @Test
    void testSelectDb_RandomDistribution_AppearsRandom() {
        CopyOnWriteArrayList<String> dataSources = new CopyOnWriteArrayList<>();
        dataSources.add("db1");
        dataSources.add("db2");
        when(dynamicDataSource.getAllAliveDataSourceKeys()).thenReturn(dataSources);

        Map<String, Integer> selectionCount = new ConcurrentHashMap<>();
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            String selected = randomRoutingStrategy.selectDb();
            selectionCount.merge(selected, 1, Integer::sum);
            DynamicDataSourceContextHolder.clearDateSourceNos();
        }

        // Both should be selected at least once (with high probability)
        assertThat(selectionCount.keySet()).containsExactlyInAnyOrder("db1", "db2");

        // Each should be selected roughly between 40% and 60% of the time
        // This is a statistical test, but with 1000 iterations it's very likely to pass
        assertThat(selectionCount.get("db1")).isBetween(300, 700);
        assertThat(selectionCount.get("db2")).isBetween(300, 700);
    }

    @Test
    void testSelectDb_WithThreeDataSources_ReturnsOneOfThem() {
        CopyOnWriteArrayList<String> dataSources = new CopyOnWriteArrayList<>();
        dataSources.add("db1");
        dataSources.add("db2");
        dataSources.add("db3");
        when(dynamicDataSource.getAllAliveDataSourceKeys()).thenReturn(dataSources);

        String selected = randomRoutingStrategy.selectDb();

        assertThat(selected).isIn(dataSources);
        assertThat(DynamicDataSourceContextHolder.getDateSourceNo()).isEqualTo(selected);
    }
}
