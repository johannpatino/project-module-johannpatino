package io.everyonecodes.deliciousnessness.model;

// The values kept for each ingredient, per 100 g. SATURATED_FAT and SUGAR are parts of FAT and CARBS.
public enum Nutrient {
    KCAL("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    FAT("Fat", "g"),
    SATURATED_FAT("of which saturated", "g"),
    CARBS("Carbohydrates", "g"),
    SUGAR("of which sugars", "g"),
    FIBER("Fiber", "g"),
    SALT("Salt", "g");

    private final String label;
    private final String unit;

    Nutrient(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public boolean isPart() {
        return this == SATURATED_FAT || this == SUGAR;
    }
}
