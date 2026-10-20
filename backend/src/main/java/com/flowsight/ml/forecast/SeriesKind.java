package com.flowsight.ml.forecast;

// Which global prior a series uses; ids match the keys in priors.json and ml-service.
public enum SeriesKind {
    INCOME("income"),
    SPEND("spend"),
    CATEGORY("category"),
    NET("net");

    private final String id;

    SeriesKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
