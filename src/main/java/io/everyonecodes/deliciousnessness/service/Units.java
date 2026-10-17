package io.everyonecodes.deliciousnessness.service;

import java.util.Map;

// Unit names as the ingredient pages and the nutrition calculation read them.
final class Units {

    private static final int MAX_LENGTH = 32;
    // Weights need no entry: 1 kg is always 1000 g.
    private static final Map<String, Double> GRAMS = Map.of("g", 1.0, "kg", 1000.0);

    private Units() {
    }

    // "Tbsp " and "tbsp" are the same unit. No unit becomes "".
    static String normalize(String unit) {
        String clean = unit == null ? "" : unit.trim().toLowerCase();
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
    }

    // Grams in one unit when it is a weight, otherwise null.
    static Double weightInGrams(String normalizedUnit) {
        return GRAMS.get(normalizedUnit);
    }
}
