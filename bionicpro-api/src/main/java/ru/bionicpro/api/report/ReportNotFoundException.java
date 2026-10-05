package ru.bionicpro.api.report;

public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(String message) {
        super(message);
    }
}
