package com.frandm.justenoughbackups.scheduler;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Standard 5-field cron expression parser and next-occurrence calculator.
 * Format: {@code <minute> <hour> <day-of-month> <month> <day-of-week>}.
 *
 * <p>DST behavior:
 * <ul>
 *     <li>Spring-forward gap (non-existent wall-clock time): shifted forward to the next valid
 *         wall-clock time via {@link ZonedDateTime#of(LocalDateTime, ZoneId)}, so a scheduled run
 *         still executes once during the transition.</li>
 *     <li>Fall-back overlap (ambiguous wall-clock time): resolves to the earlier offset and runs
 *         only once, never twice.</li>
 * </ul>
 */
public final class CronExpression {
    private static final int MAX_SEARCH_YEARS = 28;
    private static final int[] MAX_DAYS_PER_MONTH = {
            0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JAN", 1),
            Map.entry("FEB", 2),
            Map.entry("MAR", 3),
            Map.entry("APR", 4),
            Map.entry("MAY", 5),
            Map.entry("JUN", 6),
            Map.entry("JUL", 7),
            Map.entry("AUG", 8),
            Map.entry("SEP", 9),
            Map.entry("OCT", 10),
            Map.entry("NOV", 11),
            Map.entry("DEC", 12)
    );

    private static final Map<String, Integer> DOW_NAMES = Map.of(
            "SUN", 0,
            "MON", 1,
            "TUE", 2,
            "WED", 3,
            "THU", 4,
            "FRI", 5,
            "SAT", 6
    );

    private final String expression;
    private final BitSet minutes;
    private final BitSet hours;
    private final BitSet daysOfMonth;
    private final BitSet months;
    private final BitSet daysOfWeek;
    private final boolean domWildcard;
    private final boolean dowWildcard;

    private CronExpression(
            String expression,
            BitSet minutes,
            BitSet hours,
            BitSet daysOfMonth,
            BitSet months,
            BitSet daysOfWeek,
            boolean domWildcard,
            boolean dowWildcard
    ) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
        this.domWildcard = domWildcard;
        this.dowWildcard = dowWildcard;
    }

    public static CronExpression parse(String rawExpression) {
        if (rawExpression == null || rawExpression.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be empty.");
        }
        String trimmed = rawExpression.trim();
        String expanded = expandMacro(trimmed);
        String[] fields = expanded.split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("Cron expression must have 5 fields (<minute> <hour> <day-of-month> <month> <day-of-week>).");
        }

        BitSet minutes = parseField(fields[0], 0, 59, null, false, false, "minute");
        BitSet hours = parseField(fields[1], 0, 23, null, false, false, "hour");
        BitSet daysOfMonth = parseField(fields[2], 1, 31, null, false, true, "day-of-month");
        BitSet months = parseField(fields[3], 1, 12, MONTH_NAMES, false, false, "month");
        BitSet daysOfWeek = parseField(fields[4], 0, 7, DOW_NAMES, true, true, "day-of-week");

        boolean domWildcard = isWildcard(fields[2]);
        boolean dowWildcard = isWildcard(fields[4]);

        if (!domWildcard && dowWildcard && !hasReachableMonthDay(months, daysOfMonth)) {
            throw new IllegalArgumentException("Cron expression does not match any valid calendar date.");
        }

        return new CronExpression(trimmed, minutes, hours, daysOfMonth, months, daysOfWeek, domWildcard, dowWildcard);
    }

    public static ZoneId parseZoneId(String rawTimezone) {
        if (rawTimezone == null || rawTimezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        String trimmed = rawTimezone.trim();
        try {
            return ZoneId.of(trimmed);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid IANA timezone: '" + trimmed + "'.", exception);
        }
    }

    public String expression() {
        return expression;
    }

    public Optional<ZonedDateTime> nextAfter(Instant after, ZoneId zone) {
        return nextAfter(ZonedDateTime.ofInstant(after, zone));
    }

    public Optional<ZonedDateTime> nextAfter(ZonedDateTime after) {
        ZoneId zone = after.getZone();
        Instant afterInstant = after.toInstant();
        int maxYear = after.getYear() + MAX_SEARCH_YEARS;

        LocalDateTime candidate = after.toLocalDateTime()
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1);

        while (candidate.getYear() <= maxYear) {
            int month = candidate.getMonthValue();
            if (!months.get(month)) {
                int nextMonth = months.nextSetBit(month + 1);
                if (nextMonth < 0) {
                    int firstMonth = months.nextSetBit(1);
                    candidate = LocalDateTime.of(candidate.getYear() + 1, firstMonth, 1, 0, 0);
                } else {
                    candidate = LocalDateTime.of(candidate.getYear(), nextMonth, 1, 0, 0);
                }
                continue;
            }

            if (!matchesDay(candidate.toLocalDate())) {
                candidate = candidate.toLocalDate().plusDays(1).atStartOfDay();
                continue;
            }

            int hour = candidate.getHour();
            if (!hours.get(hour)) {
                int nextHour = hours.nextSetBit(hour + 1);
                if (nextHour < 0) {
                    candidate = candidate.toLocalDate().plusDays(1).atStartOfDay();
                } else {
                    candidate = candidate.withHour(nextHour).withMinute(0);
                }
                continue;
            }

            int minute = candidate.getMinute();
            if (!minutes.get(minute)) {
                int nextMinute = minutes.nextSetBit(minute + 1);
                if (nextMinute < 0) {
                    candidate = candidate.plusHours(1).withMinute(0);
                } else {
                    candidate = candidate.withMinute(nextMinute);
                }
                continue;
            }

            ZonedDateTime resolved = ZonedDateTime.of(candidate, zone);
            if (resolved.toInstant().isAfter(afterInstant)) {
                return Optional.of(resolved);
            }

            // Can happen during a DST fall-back overlap or after a spring-forward shift;
            // advance by one local minute until strictly after afterInstant.
            candidate = candidate.plusMinutes(1);
        }

        return Optional.empty();
    }

    private boolean matchesDay(LocalDate date) {
        boolean domMatch = daysOfMonth.get(date.getDayOfMonth());
        boolean dowMatch = daysOfWeek.get(cronDayOfWeek(date.getDayOfWeek()));
        if (domWildcard && dowWildcard) {
            return true;
        }
        if (domWildcard) {
            return dowMatch;
        }
        if (dowWildcard) {
            return domMatch;
        }
        return domMatch || dowMatch;
    }

    private static int cronDayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SUNDAY ? 0 : dayOfWeek.getValue();
    }

    private static String expandMacro(String expression) {
        return switch (expression.toLowerCase(Locale.ROOT)) {
            case "@yearly", "@annually" -> "0 0 1 1 *";
            case "@monthly" -> "0 0 1 * *";
            case "@weekly" -> "0 0 * * 0";
            case "@daily", "@midnight" -> "0 0 * * *";
            case "@hourly" -> "0 * * * *";
            default -> expression;
        };
    }

    private static boolean isWildcard(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    private static boolean hasReachableMonthDay(BitSet months, BitSet daysOfMonth) {
        for (int month = months.nextSetBit(1); month >= 0; month = months.nextSetBit(month + 1)) {
            int maxDay = MAX_DAYS_PER_MONTH[month];
            for (int day = daysOfMonth.nextSetBit(1); day >= 0 && day <= maxDay; day = daysOfMonth.nextSetBit(day + 1)) {
                return true;
            }
        }
        return false;
    }

    private static BitSet parseField(
            String field,
            int min,
            int max,
            Map<String, Integer> names,
            boolean wrapSevenToZero,
            boolean allowQuestionMark,
            String fieldName
    ) {
        BitSet bits = new BitSet(max + 1);
        String[] parts = field.split(",", -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Empty item in cron " + fieldName + " field: '" + field + "'.");
            }
            parsePart(part, min, max, names, wrapSevenToZero, allowQuestionMark, fieldName, bits);
        }
        if (bits.isEmpty()) {
            throw new IllegalArgumentException("Cron " + fieldName + " field matches no values: '" + field + "'.");
        }
        return bits;
    }

    private static void parsePart(
            String part,
            int min,
            int max,
            Map<String, Integer> names,
            boolean wrapSevenToZero,
            boolean allowQuestionMark,
            String fieldName,
            BitSet target
    ) {
        String rangePart = part;
        int step = 1;
        int slashIndex = part.indexOf('/');
        if (slashIndex >= 0) {
            if (part.indexOf('/', slashIndex + 1) >= 0) {
                throw new IllegalArgumentException("Invalid step syntax in cron " + fieldName + " field: '" + part + "'.");
            }
            rangePart = part.substring(0, slashIndex);
            String stepStr = part.substring(slashIndex + 1);
            if (stepStr.isEmpty()) {
                throw new IllegalArgumentException("Missing step value in cron " + fieldName + " field: '" + part + "'.");
            }
            step = parsePositiveInt(stepStr, fieldName);
        }

        int rangeStart;
        int rangeEnd;
        if ("*".equals(rangePart) || (allowQuestionMark && "?".equals(rangePart))) {
            rangeStart = min;
            rangeEnd = wrapSevenToZero ? 6 : max;
        } else {
            int dashIndex = rangePart.indexOf('-');
            if (dashIndex >= 0) {
                if (rangePart.indexOf('-', dashIndex + 1) >= 0) {
                    throw new IllegalArgumentException("Invalid range in cron " + fieldName + " field: '" + part + "'.");
                }
                rangeStart = parseValue(rangePart.substring(0, dashIndex), min, max, names, fieldName);
                rangeEnd = parseValue(rangePart.substring(dashIndex + 1), min, max, names, fieldName);
                if (rangeStart > rangeEnd) {
                    throw new IllegalArgumentException("Range start exceeds end in cron " + fieldName + " field: '" + part + "'.");
                }
            } else {
                rangeStart = parseValue(rangePart, min, max, names, fieldName);
                if (slashIndex >= 0) {
                    if (wrapSevenToZero && rangeStart == 7) {
                        rangeStart = 0;
                    }
                    rangeEnd = wrapSevenToZero ? 6 : max;
                } else {
                    rangeEnd = rangeStart;
                }
            }
        }

        for (int value = rangeStart; value <= rangeEnd; value += step) {
            int normalized = (wrapSevenToZero && value == 7) ? 0 : value;
            target.set(normalized);
        }
    }

    private static int parseValue(String token, int min, int max, Map<String, Integer> names, String fieldName) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Empty value in cron " + fieldName + " field.");
        }
        if (names != null) {
            Integer named = names.get(token.toUpperCase(Locale.ROOT));
            if (named != null) {
                return named;
            }
        }
        int parsed = parseNonNegativeInt(token, fieldName);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("Value " + parsed + " out of range [" + min + ", " + max + "] in cron " + fieldName + " field.");
        }
        return parsed;
    }

    private static int parsePositiveInt(String token, String fieldName) {
        int value = parseNonNegativeInt(token, fieldName);
        if (value <= 0) {
            throw new IllegalArgumentException("Expected positive integer in cron " + fieldName + " field: '" + token + "'.");
        }
        return value;
    }

    private static int parseNonNegativeInt(String token, String fieldName) {
        try {
            int value = Integer.parseInt(token);
            if (value < 0) {
                throw new IllegalArgumentException("Negative value not allowed in cron " + fieldName + " field: '" + token + "'.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number in cron " + fieldName + " field: '" + token + "'.", exception);
        }
    }
}
