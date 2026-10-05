package ru.bionicpro.api.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.bionicpro.api.auth.SessionAuthFilter;
import ru.bionicpro.api.auth.SessionUser;
import ru.bionicpro.api.report.ReportDay;
import ru.bionicpro.api.report.ReportLink;
import ru.bionicpro.api.report.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * API отчётов. Пользователь отчёта всегда берётся из серверной сессии,
 * поэтому получить чужой отчёт через параметры запроса нельзя.
 */
@RestController
@RequestMapping("/reports")
public class ReportsController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Ссылка на готовый отчёт. Файл берётся из объектного хранилища, если он там уже есть,
     * иначе формируется из витрины и загружается в S3; браузеру отдаётся адрес CDN.
     */
    @GetMapping
    public ReportLink report(
            @RequestAttribute(SessionAuthFilter.SESSION_ATTRIBUTE) SessionUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.link(user.username(), from, to);
    }

    /** Прямая выгрузка отчёта из витрины — без объектного хранилища и кеша. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestAttribute(SessionAuthFilter.SESSION_ATTRIBUTE) SessionUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<ReportDay> days = reportService.report(user.username(), from, to);
        if (days.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"за выбранный период данных нет\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String fileName = "report-" + user.username() + "-" + LocalDate.now().format(FILE_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(reportService.toCsv(days));
    }

    /** Что доступно пользователю: за какой период витрина подготовлена. */
    @GetMapping("/availability")
    public Map<String, Object> availability(
            @RequestAttribute(SessionAuthFilter.SESSION_ATTRIBUTE) SessionUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<ReportDay> days = reportService.report(user.username(), from, to);
        return Map.of(
                "login", user.username(),
                "availablePeriod", reportService.availablePeriod(),
                "summary", reportService.summary(days));
    }
}
