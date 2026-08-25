package com.hopoong.order.config.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

import java.util.Locale;

public class OrderP6spyPrettySqlFormatter implements MessageFormattingStrategy {

    private static volatile long slowQueryThresholdMs = 1000;

    public static void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
        OrderP6spyPrettySqlFormatter.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    @Override
    public String formatMessage(
            int connectionId,
            String now,
            long elapsed,
            String category,
            String prepared,
            String sql,
            String url
    ) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        if (elapsed >= slowQueryThresholdMs) {
            return "";
        }

        return formatNormalMessage(connectionId, sql);
    }

    public static String formatSlowMessage(int connectionId, long elapsedMs, String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        String formattedSql = formatSql(sql);

        return System.lineSeparator()
                + "-------------------- SLOW SQL --------------------"
                + System.lineSeparator()
                + "Connection ID : " + connectionId
                + System.lineSeparator()
                + "Execution Time: " + elapsedMs + " ms"
                + System.lineSeparator()
                + "SQL:"
                + System.lineSeparator()
                + formattedSql
                + System.lineSeparator()
                + "--------------------------------------------------";
    }

    private static String formatNormalMessage(int connectionId, String sql) {
        String formattedSql = formatSql(sql);

        return System.lineSeparator()
                + "-------------------- SQL --------------------"
                + System.lineSeparator()
                + "Connection ID : " + connectionId
                + System.lineSeparator()
                + "SQL:"
                + System.lineSeparator()
                + formattedSql
                + System.lineSeparator()
                + "---------------------------------------------";
    }

    private static String formatSql(String sql) {
        String trimmedSql = sql.trim().toLowerCase(Locale.ROOT);

        if (trimmedSql.startsWith("select")
                || trimmedSql.startsWith("insert")
                || trimmedSql.startsWith("update")
                || trimmedSql.startsWith("delete")) {
            return FormatStyle.BASIC.getFormatter().format(sql);
        }

        return sql;
    }
}
