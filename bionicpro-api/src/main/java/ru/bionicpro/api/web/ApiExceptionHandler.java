package ru.bionicpro.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.bionicpro.api.report.PeriodNotProcessedException;
import ru.bionicpro.api.report.ReportNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Пользователь может запросить данные, которых в OLAP ещё нет. */
    @ExceptionHandler(PeriodNotProcessedException.class)
    public ResponseEntity<Map<String, Object>> handlePeriod(PeriodNotProcessedException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        body.put("availablePeriod", e.getAvailablePeriod());
        body.put("hint", e.getAvailablePeriod() == null
                ? "витрина ещё не собиралась: дождитесь запуска ETL-процесса"
                : "отчёт доступен по " + e.getAvailablePeriod());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ReportNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
