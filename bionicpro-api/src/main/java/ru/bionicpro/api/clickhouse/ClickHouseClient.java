package ru.bionicpro.api.clickhouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.bionicpro.api.config.ApiProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Запросы к OLAP-базе ClickHouse по HTTP-интерфейсу.
 * Все вычисления выполняются в ClickHouse, сервис только читает готовую витрину.
 */
@Component
public class ClickHouseClient {

    private final RestClient http;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public ClickHouseClient(RestClient.Builder builder, ApiProperties properties, ObjectMapper objectMapper) {
        this.http = builder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> query(String sql) {
        String response = execute(sql + " FORMAT JSONEachRow");
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (String line : response.split("\n")) {
                if (!line.isBlank()) {
                    rows.add(objectMapper.readValue(line, LinkedHashMap.class));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("не удалось разобрать ответ ClickHouse", e);
        }
        return rows;
    }

    public Optional<String> queryScalar(String sql) {
        String response = execute(sql + " FORMAT TabSeparated").trim();
        return response.isEmpty() || "\\N".equals(response) ? Optional.empty() : Optional.of(response);
    }

    private String execute(String body) {
        try {
            String result = http.post()
                    .uri(properties.getClickhouse().getUrl() + "/?database=" + properties.getClickhouse().getDatabase())
                    .header("X-ClickHouse-User", properties.getClickhouse().getUser())
                    .header("X-ClickHouse-Key", properties.getClickhouse().getPassword())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return result == null ? "" : result;
        } catch (Exception e) {
            throw new IllegalStateException("запрос в ClickHouse не выполнен: " + e.getMessage(), e);
        }
    }
}
