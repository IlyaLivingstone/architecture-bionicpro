package ru.bionicpro.api.report;

/** Данные за запрошенный период ещё не подготовлены ETL-процессом. */
public class PeriodNotProcessedException extends RuntimeException {

    private final String availablePeriod;

    public PeriodNotProcessedException(String availablePeriod) {
        super("данные за запрошенный период ещё не подготовлены");
        this.availablePeriod = availablePeriod;
    }

    public String getAvailablePeriod() {
        return availablePeriod;
    }
}
