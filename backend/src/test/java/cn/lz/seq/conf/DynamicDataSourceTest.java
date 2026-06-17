package cn.lz.seq.conf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void testInitialState_AllDataSourcesAlive() {
        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsExactlyInAnyOrder("db1", "db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).isEmpty();
    }

    @Test
    void testMarkAsFault_RemovesFromAliveList() {
        dynamicDataSource.markAsFault("db1");

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).containsKey("db1");
    }

    @Test
    void testSelectAliveDataSource_ExcludesSpecifiedKey() {
        String selected = dynamicDataSource.selectAliveDataSource("db1");

        assertThat(selected).isEqualTo("db2");
    }

    @Test
    void testSelectAliveDataSource_ReturnsAnyWhenNoExclusion() {
        String selected = dynamicDataSource.selectAliveDataSource(null);

        assertThat(selected).isIn("db1", "db2");
    }

    @Test
    void testSelectAliveDataSource_FallbackToAllWhenAllFaulted() {
        dynamicDataSource.markAsFault("db1");
        dynamicDataSource.markAsFault("db2");

        String selected = dynamicDataSource.selectAliveDataSource(null);

        assertThat(selected).isIn("db1", "db2");
    }

    @Test
    void testMarkAsFault_MultipleTimesSameKey() {
        dynamicDataSource.markAsFault("db1");
        dynamicDataSource.markAsFault("db1");

        assertThat(dynamicDataSource.getAllAliveDataSourceKeys()).containsOnly("db2");
        assertThat(dynamicDataSource.getFaultDataSourceMap()).hasSize(1);
    }

    @Test
    void testDetermineCurrentLookupKey_ReturnsContextDataSource() {
        DynamicDataSourceContextHolder.setDataSourceNo("db1");

        Object lookupKey = dynamicDataSource.determineCurrentLookupKey();

        assertThat(lookupKey).isEqualTo("db1");

        DynamicDataSourceContextHolder.clearDataSourceNos();
    }
}
