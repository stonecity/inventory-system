package com.dream.inventory.common;

import java.time.ZoneId;

public final class TimeZones {

    public static final String ID = "Asia/Shanghai";
    public static final ZoneId ZONE = ZoneId.of(ID);

    private TimeZones() {
    }
}
