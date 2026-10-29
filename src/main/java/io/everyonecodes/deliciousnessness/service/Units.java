package io.everyonecodes.deliciousnessness.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Unit names as the ingredient pages, the nutrition calculation and the shopping list read them.
// Every spelling of one unit (tl, łyżeczka, teaspoon, ...) becomes the same name here.
final class Units {

    private static final int MAX_LENGTH = 32;
    // Weights need no entry: 1 kg is always 1000 g.
    private static final Map<String, Double> GRAMS = Map.of("g", 1.0, "kg", 1000.0);

    // Spelling -> the one name the app uses. Keys are lower case, without a trailing dot.
    private static final Map<String, String> SAME_AS = new HashMap<>();

    static {
        spellings("tsp", "tsp", "teaspoon", "teaspoons", "english teaspoon", "tl", "teelöffel", "cdta", "cdita",
                "cucharadita", "cucharaditas", "łyżeczka", "łyżeczki", "łyżeczkę", "łyżeczek", "łyzeczki", "łyżczki");
        spellings("tbsp", "tbsp", "tbs", "tbl", "tablespoon", "tablespoons", "english tablespoon", "el",
                "esslöffel", "cda", "cucharada", "cucharadas", "łyżka", "łyżki", "łyżkę", "łyżką", "łyżek", "łyźki");
        spellings("cup", "cup", "cups", "szklanka", "szklanki", "szklanek", "filiżanka", "filiżanki", "filiżankę",
                "becher", "tasse", "tassen", "taza", "tazas");
        spellings("g", "g", "gr", "gram", "grams", "gramm", "gramy", "gramów", "grama");
        spellings("kg", "kg", "kilo", "kilogram", "kilograms", "kilogramm", "kilogramy", "kilogramów");
        spellings("ml", "ml", "mililitr", "mililitry", "mililitrów", "milliliter", "milliliters", "millilitre",
                "mililitros");
        spellings("l", "l", "litr", "litry", "litrów", "liter", "liters", "litre", "litres", "litro", "litros");
        spellings("clove", "clove", "cloves", "ząbek", "ząbki", "ząbków", "ząbekq", "zehe", "zehen", "diente",
                "dientes");
        spellings("piece", "piece", "pieces", "pc", "pcs", "szt", "sztuka", "sztuki", "sztuk", "stück", "stk", "st",
                "pieza", "piezas");
        spellings("pinch", "pinch", "pinches", "szczypta", "szczypty", "szczypt", "prise", "pizca", "pizcas");
        spellings("can", "can", "cans", "tin", "tins", "puszka", "puszki", "puszek", "dose", "dosen", "lata", "latas");
        spellings("handful", "handful", "handfuls", "garść", "garści", "garście", "handvoll", "puñado", "puñados");
        spellings("bunch", "bunch", "bunches", "pęczek", "pęczki", "pęczków", "bund", "bündel", "manojo", "manojos");
        spellings("sprig", "sprig", "sprigs", "gałązka", "gałązki", "gałązek", "zweig", "zweige", "ramita", "ramitas");
        spellings("stalk", "stalk", "stalks", "łodyga", "łodygi", "łodyg", "stange", "stangen", "stengel", "stiel",
                "tallo", "tallos");
        spellings("leaf", "leaf", "leaves", "liść", "liście", "liści", "blatt", "blätter", "hoja", "hojas");
        spellings("slice", "slice", "slices", "plaster", "plastry", "plastrów", "plasterek", "scheibe", "scheiben",
                "rodaja", "rodajas", "rebanada", "rebanadas");
        spellings("sachet", "sachet", "sachets", "saszetka", "saszetki", "saszetek", "tüte", "tüten", "sobre", "sobres");
        spellings("head", "head", "heads", "główka", "główki", "główek", "kopf", "köpfe", "cabeza", "cabezas");
        spellings("blade", "blade", "blades", "źdźbło", "źdźbła", "źdżbła");
        spellings("stick", "stick", "sticks", "laska", "laski", "lasek");
    }

    private Units() {
    }

    // "Tbsp " and "tbsp" are the same unit. "tl." and "tl" are the same unit. No unit becomes "".
    static String normalize(String unit) {
        String clean = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\.+$", "")
                .replaceAll("\\s+", " ");
        String name = SAME_AS.getOrDefault(clean, clean);
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }

    // Units that count things (one clove, one piece). The shopping list keeps them as counts; they are never
    // turned into grams there.
    private static final Set<String> COUNTED = Set.of("", "clove", "piece", "pinch", "can", "handful", "bunch",
            "sprig", "stalk", "leaf", "slice", "sachet", "head", "blade", "stick");

    static boolean isCounted(String normalizedUnit) {
        return COUNTED.contains(normalizedUnit);
    }

    // Grams in one unit when it is a weight, otherwise null.
    static Double weightInGrams(String normalizedUnit) {
        return GRAMS.get(normalizedUnit);
    }

    private static void spellings(String name, String... alternatives) {
        for (String alternative : alternatives) {
            if (SAME_AS.put(alternative, name) != null) {
                throw new IllegalStateException("Unit spelling listed twice: " + alternative);
            }
        }
    }
}
