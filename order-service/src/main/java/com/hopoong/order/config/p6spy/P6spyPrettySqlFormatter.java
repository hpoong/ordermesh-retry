package com.hopoong.order.config.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.FormatStyle;

import java.util.Locale;

public class P6spyPrettySqlFormatter implements MessageFormattingStrategy {

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

        String formattedSql = formatSql(sql);

        return System.lineSeparator()
                + "-------------------- SQL --------------------"
                + System.lineSeparator()
                + "Connection ID : " + connectionId
                + System.lineSeparator()
//                + "Execution Time: " + elapsed + " ms"
//                + System.lineSeparator()
//                + "Category      : " + category
//                + System.lineSeparator()
//                + "URL           : " + url
//                + System.lineSeparator()
                + "SQL:"
                + System.lineSeparator()
                + formattedSql
                + System.lineSeparator()
                + "---------------------------------------------";
    }

    private String formatSql(String sql) {
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