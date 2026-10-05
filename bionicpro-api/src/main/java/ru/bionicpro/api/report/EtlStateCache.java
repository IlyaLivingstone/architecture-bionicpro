package ru.bionicpro.api.report;

import org.springframework.stereotype.Component;
import ru.bionicpro.api.clickhouse.ClickHouseClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Состояние витрины с коротким кешем в памяти.
 * Повторные запросы уже сформированных отчётов не обращаются к OLAP-базе:
 * сведения о периоде и версии витрины берутся из кеша.
 */
@Component
public class EtlStateCache {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final ClickHouseClient clickhouse;

    private volatile EtlState cached;
    private volatile Instant expiresAt = Instant.EPOCH;

    public EtlStateCache(ClickHouseClient clickhouse) {
        this.clickhouse = clickhouse;
    }

    public Optional<EtlState> current() {
        if (cached != null && Instant.now().isBefore(expiresAt)) {
            return Optional.of(cached);
        }
        Optional<String> period = clickhouse.queryScalar(
                "SELECT toString(max(last_period)) FROM etl_state WHERE job = 'reports_mart'");
        if (period.isEmpty()) {
            return Optional.empty();
        }
        String version = clickhouse.queryScalar(
                        "SELECT toString(max(finished_at)) FROM etl_state WHERE job = 'reports_mart'")
                .map(value -> value.replace("-", "").replace(":", "").replace(" ", "T"))
                .orElse("unknown");
        String crmVersion = clickhouse.queryScalar(
                        "SELECT toString(max(version)) FROM crm_clients")
                .orElse("0");
        cached = new EtlState(period.get(), version, crmVersion);
        expiresAt = Instant.now().plus(TTL);
        return Optional.of(cached);
    }
}
