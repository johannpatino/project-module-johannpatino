package io.everyonecodes.deliciousnessness.model;

// The two columns of each day. Fixed on purpose: this planner is for the two of you.
public enum PlannerPerson {
    ELA, JOHANN;

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
