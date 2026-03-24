package cn.lz.seq.dao;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SeqDao {

    /**
     * 只允许字母、数字和下划线的表名，防止SQL注入
     */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * 不知为什么，使用jdbcTemplate比直接使用DruidDataSource效率更高
     */
    @Resource
    private JdbcTemplate jdbcTemplate;

    @DynamicSwitch() // 使用注解进行增强
    public BigInteger getSeq(String tableName) {
        // 校验表名，防止SQL注入
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            String sql = "replace into " + tableName + " (stub) values ('0')";
            log.debug("执行sql: {}", sql);
            jdbcTemplate.update(new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"}); // "id" 是主键列的列名
                    return ps;
                }
            }, keyHolder);
            var res = keyHolder.getKeyList().get(0).get("GENERATED_KEY");
            return (BigInteger) res;
        } catch (Exception e) {
            log.error("从数据库获取id失败 ", e);
            throw e;
        }
    }
}
