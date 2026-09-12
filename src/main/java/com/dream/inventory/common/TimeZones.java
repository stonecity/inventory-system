package com.dream.inventory.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeZones {

    public static final String ID = "Asia/Shanghai";
    public static final ZoneId ZONE = ZoneId.of(ID);

    /** 用于 CSV 等文本输出的北京时间格式 */
    public static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);

    public static String format(Instant instant) {
        return instant == null ? "" : CSV_TIME.format(instant);
    }

    private TimeZones() {
    }
}
