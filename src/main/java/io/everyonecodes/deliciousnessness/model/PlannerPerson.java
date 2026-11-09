package io.everyonecodes.deliciousnessness.model;

// The two columns of each day. Fixed on purpose: this planner is for the two of you.
// portion: how much of one planned serving this person really eats. Only the day's nutrition uses it.
public enum PlannerPerson {
    ELA(0.8), JOHANN(1.2);

    private final double portion;

    PlannerPerson(double portion) {
        this.portion = portion;
    }

    public double portion() {
        return portion;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
