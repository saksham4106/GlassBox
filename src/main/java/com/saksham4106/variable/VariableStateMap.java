package com.saksham4106.variable;

import java.util.ArrayList;
import java.util.List;

public class VariableStateMap {

    private final int lineNumber;
    private final String frameID;
    private final List<VariableState> variableStates;

    public VariableStateMap(int lineNumber, String frameID){
        variableStates = new ArrayList<>();
        this.lineNumber = lineNumber;
        this.frameID = frameID;
    }

    public void addVariable(VariableState variableState){
        variableStates.add(variableState);
    }

    public void addVariable(String name, VariableValue variableValue){
        variableStates.add(new VariableState(name, variableValue));
    }

    public List<VariableState> getVariableStates() {
        return variableStates;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getFrameID() {
        return frameID;
    }
}
