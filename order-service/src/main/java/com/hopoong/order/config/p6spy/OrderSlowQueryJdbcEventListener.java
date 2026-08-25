package com.hopoong.order.config.p6spy;

import com.hopoong.order.config.properties.OrderSlowQueryProperties;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OrderSlowQueryJdbcEventListener extends SimpleJdbcEventListener {

    private static final Logger SLOW_SQL_LOGGER = LoggerFactory.getLogger("slow.sql");

    private final OrderSlowQueryProperties properties;

    @Override
    public void onAfterAnyExecute(
            StatementInformation statementInformation,
            long timeElapsedNanos,
            SQLException e
    ) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(timeElapsedNanos);
        if (elapsedMs < properties.getThresholdMs()) {
            return;
        }

        String sql = resolveSql(statementInformation);
        if (sql == null || sql.isBlank()) {
            return;
        }

        SLOW_SQL_LOGGER.warn(
                OrderP6spyPrettySqlFormatter.formatSlowMessage(
                        statementInformation.getConnectionInformation().getConnectionId(),
                        elapsedMs,
                        sql
                )
        );
    }

    private String resolveSql(StatementInformation statementInformation) {
        String sql = statementInformation.getSqlWithValues();
        if (sql == null || sql.isBlank()) {
            sql = statementInformation.getSql();
        }
        return sql;
    }
}
