package cn.lz.seq.conf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DynamicDataSource.
 * Tests fault marking, 10-second recovery, and active list management.
 */
class DynamicDataSourceTest {

    private DynamicDataSource dynamicDataSource;
    private CopyOnWriteArrayList<String> aliveKeys;

    @BeforeEach
    void setUp() {
        dynamicDataSource = new DynamicDataSource();
        aliveKeys = new CopyOnWriteArrayList<>();
        aliveKeys.add("db1");
        aliveKeys.add("db2");
        dynamicDataSource.setAllAliveDataSourceKeys(aliveKeys);
        dynamicDataSource.setFaultDataSourceMap(new ConcurrentHashMap<>());
    }

    @Test
    void testInitialState_AllDataSourcesAlive() {
        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsExactlyInAnyOrder("db1", "db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).isEmpty();
    }

    @Test
    void testMarkFaultDataSource_FaultyDataSourceRemovedFromAliveList() {
        // Mark db1 as faulty
        long now = System.currentTimeMillis();
        dynamicDataSource.getFaultDataSourceMap().put("db1", now);

        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).containsKey("db1");
    }

    @Test
    void testFaultRecovery_After10Seconds() {
        // Mark db1 as faulty with timestamp older than 10 seconds
        long tenSecondsAgo = System.currentTimeMillis() - 10001;
        dynamicDataSource.getFaultDataSourceMap().put("db1", tenSecondsAgo);

        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsExactlyInAnyOrder("db1", "db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).isEmpty();
    }

    @Test
    void testNoRecovery_Before10Seconds() {
        // Mark db1 as faulty with timestamp within 10 seconds
        long fiveSecondsAgo = System.currentTimeMillis() - 5000;
        dynamicDataSource.getFaultDataSourceMap().put("db1", fiveSecondsAgo);

        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).containsKey("db1");
    }

    @Test
    void testMultipleFaultyDataSources_PartialRecovery() {
        long tenSecondsAgo = System.currentTimeMillis() - 10001;
        long fiveSecondsAgo = System.currentTimeMillis() - 5000;
        dynamicDataSource.getFaultDataSourceMap().put("db1", tenSecondsAgo);
        dynamicDataSource.getFaultDataSourceMap().put("db2", fiveSecondsAgo);

        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db1");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).containsOnlyKeys("db2");
    }

    @Test
    void testDetermineCurrentLookupKey_ReturnsContextDataSource() {
        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        Object lookupKey = dynamicDataSource.determineCurrentLookupKey();

        assertThat(lookupKey).isEqualTo("db1");

        DynamicDataSourceContextHolder.clearDataSourceNos();
    }

    @Test
    void testHandleFaultDataSource_EmptyFaultMap_DoesNothing() {
        // This test ensures no exceptions are thrown when fault map is empty
        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsExactlyInAnyOrder("db1", "db2");
    }

    @Test
    void testMarkSameFaultMultipleTimes_OnlyOneEntry() {
        long now = System.currentTimeMillis();
        dynamicDataSource.getFaultDataSourceMap().put("db1", now);
        dynamicDataSource.getFaultDataSourceMap().put("db1", now - 1000); // Overwrite

        dynamicDataSource.handleFaultDataSource();

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).hasSize(1);
    }
}
