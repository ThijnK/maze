package org.academic.symbolicx.executor;

import java.util.HashMap;
import java.util.Map;

public class SymbolicState {
    private Map<String, String> symbolicVariables;
    private StringBuilder pathCondition;

    public SymbolicState() {
        this.symbolicVariables = new HashMap<>();
        this.pathCondition = new StringBuilder("true"); // Default path condition
    }

    public SymbolicState(Map<String, String> symbolicVariables, StringBuilder pathCondition) {
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.pathCondition = new StringBuilder(pathCondition);
    }

    public void setVariable(String var, String expression) {
        symbolicVariables.put(var, expression);
    }

    public String getVariable(String var) {
        return symbolicVariables.getOrDefault(var, var); // Default to itself if not found
    }

    public void addPathCondition(String condition) {
        if (pathCondition.toString().equals("true")) {
            pathCondition = new StringBuilder(condition);
        } else {
            pathCondition.append(" && ").append(condition);
        }
    }

    public String getPathCondition() {
        return pathCondition.toString();
    }

    public SymbolicState clone() {
        SymbolicState newState = new SymbolicState(symbolicVariables, pathCondition);
        return newState;
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", PathCondition: " + pathCondition;
    }
}
