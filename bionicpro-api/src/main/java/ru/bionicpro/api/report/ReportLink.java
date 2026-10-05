package ru.bionicpro.api.report;

public record ReportLink(String login,
                         String period,
                         boolean cached,
                         String etlVersion,
                         String url) {
}
