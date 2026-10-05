package ru.bionicpro.api.report;

import java.util.Map;

public record ReportDay(String date,
                        String fullName,
                        String prosthesisModel,
                        String country,
                        long sessions,
                        long movements,
                        double avgResponseMs,
                        long maxResponseMs,
                        long errors,
                        int minBattery) {

    public static ReportDay from(Map<String, Object> row) {
        return new ReportDay(
                String.valueOf(row.get("report_date")),
                String.valueOf(row.get("full_name")),
                String.valueOf(row.get("prosthesis_model")),
                String.valueOf(row.get("country")),
                asLong(row.get("sessions")),
                asLong(row.get("movements")),
                asDouble(row.get("avg_response_ms")),
                asLong(row.get("max_response_ms")),
                asLong(row.get("errors")),
                (int) asLong(row.get("min_battery")));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }
}
