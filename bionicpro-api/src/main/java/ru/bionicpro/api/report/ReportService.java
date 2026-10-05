package ru.bionicpro.api.report;

import org.springframework.stereotype.Service;
import ru.bionicpro.api.clickhouse.ClickHouseClient;
import ru.bionicpro.api.storage.ReportStorage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Отчёты собираются из готовой витрины ClickHouse и складываются в объектное
 * хранилище, откуда их раздаёт CDN. Повторный запрос уже сформированного отчёта
 * не доходит до OLAP-базы: файл берётся из S3, а сведения о витрине — из кеша.
 */
@Service
public class ReportService {

    private final ClickHouseClient clickhouse;
    private final ReportStorage storage;
    private final EtlStateCache etlState;

    public ReportService(ClickHouseClient clickhouse, ReportStorage storage, EtlStateCache etlState) {
        this.clickhouse = clickhouse;
        this.storage = storage;
        this.etlState = etlState;
    }

    public String availablePeriod() {
        return etlState.current().map(EtlState::period).orElse(null);
    }

    /**
     * Ссылка на отчёт в CDN.
     * Если отчёт уже сформирован для текущей версии витрины — он берётся из S3,
     * иначе данные читаются из ClickHouse и файл загружается в хранилище.
     */
    public ReportLink link(String login, LocalDate from, LocalDate to) {
        EtlState state = etlState.current()
                .orElseThrow(() -> new PeriodNotProcessedException(null));
        LocalDate lastProcessed = LocalDate.parse(state.period());
        if ((from != null && from.isAfter(lastProcessed)) || (to != null && to.isAfter(lastProcessed))) {
            throw new PeriodNotProcessedException(state.period());
        }

        // версия отчёта меняется и при обновлении витрины, и при изменении данных CRM
        String reportVersion = state.version() + "-" + state.crmVersion();
        if (from == null && to == null) {
            String latest = storage.findLatest(login);
            if (latest != null && latest.contains(reportVersion)) {
                return new ReportLink(login, periodOf(latest), true, reportVersion,
                        storage.presignedCdnUrl(latest));
            }
        }

        String period = periodLabel(from, to, state.period());
        String key = login + "/" + period + "/usage-" + reportVersion + ".csv";
        if (storage.exists(key)) {
            return new ReportLink(login, period, true, reportVersion, storage.presignedCdnUrl(key));
        }

        List<ReportDay> days = query(login, from, to);
        if (days.isEmpty()) {
            throw new ReportNotFoundException("за выбранный период данных нет");
        }
        storage.put(key, toCsv(days), "text/csv; charset=utf-8");
        // прошлые версии отчёта больше не нужны: ключ изменился вместе с версией витрины
        storage.deleteOlderVersions(login, key);
        return new ReportLink(login, period, false, reportVersion, storage.presignedCdnUrl(key));
    }

    /** Прямая выгрузка из витрины — без объектного хранилища и кеша. */
    public List<ReportDay> report(String login, LocalDate from, LocalDate to) {
        EtlState state = etlState.current()
                .orElseThrow(() -> new PeriodNotProcessedException(null));
        LocalDate lastProcessed = LocalDate.parse(state.period());
        if ((from != null && from.isAfter(lastProcessed)) || (to != null && to.isAfter(lastProcessed))) {
            throw new PeriodNotProcessedException(state.period());
        }
        return query(login, from, to);
    }

    private String periodLabel(LocalDate from, LocalDate to, String lastProcessed) {
        String start = from != null ? from.toString() : "all";
        String end = to != null ? to.toString() : lastProcessed;
        return start + "_" + end;
    }

    /** Период восстановлен из ключа объекта: <login>/<период>/usage-<версия>.csv */
    private String periodOf(String key) {
        String[] parts = key.split("/");
        return parts.length > 1 ? parts[1] : "all";
    }

    private List<ReportDay> query(String login, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT report_date, full_name, prosthesis_model, country, sessions, movements, "
                        + "avg_response_ms, max_response_ms, errors, min_battery "
                        + "FROM user_usage_mart FINAL WHERE login = '" + escape(login) + "'");
        if (from != null) {
            sql.append(" AND report_date >= toDate('").append(from).append("')");
        }
        if (to != null) {
            sql.append(" AND report_date <= toDate('").append(to).append("')");
        }
        sql.append(" ORDER BY report_date");
        return clickhouse.query(sql.toString()).stream().map(ReportDay::from).toList();
    }

    /** Отчёт в CSV: так его удобно открыть в таблице. */
    public byte[] toCsv(List<ReportDay> days) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Дата;Клиент;Модель протеза;Страна;Сессии;Движения;")
                .append("Среднее время отклика, мс;Максимальное время отклика, мс;Ошибки;Минимальный заряд, %\n");
        for (ReportDay day : days) {
            csv.append(day.date()).append(';')
                    .append(day.fullName()).append(';')
                    .append(day.prosthesisModel()).append(';')
                    .append(day.country()).append(';')
                    .append(day.sessions()).append(';')
                    .append(day.movements()).append(';')
                    .append(day.avgResponseMs()).append(';')
                    .append(day.maxResponseMs()).append(';')
                    .append(day.errors()).append(';')
                    .append(day.minBattery()).append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "''").replace("\\", "");
    }

    /** Сводка по отчёту — используется веб-приложением для подписи кнопки. */
    public Map<String, Object> summary(List<ReportDay> days) {
        long movements = days.stream().mapToLong(ReportDay::movements).sum();
        long errors = days.stream().mapToLong(ReportDay::errors).sum();
        double avg = days.stream().mapToDouble(ReportDay::avgResponseMs).average().orElse(0);
        return Map.of("days", days.size(), "movements", movements, "errors", errors,
                "avgResponseMs", Math.round(avg * 10) / 10.0);
    }
}
