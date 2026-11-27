package com.nossoteto.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class AppTime {

    public static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private AppTime() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), APP_ZONE);
    }

    public static ZonedDateTime nowInAppZone(Clock clock) {
        return clock.instant().atZone(APP_ZONE);
    }

    public static LocalDateTime nowUtc(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public static LocalDateTime monthStartUtc(Clock clock) {
        LocalDate firstOfMonth = today(clock).withDayOfMonth(1);
        return firstOfMonth.atStartOfDay(APP_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public static LocalDateTime nextMonthStartUtc(Clock clock) {
        LocalDate firstOfNextMonth = today(clock).withDayOfMonth(1).plusMonths(1);
        return firstOfNextMonth.atStartOfDay(APP_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
