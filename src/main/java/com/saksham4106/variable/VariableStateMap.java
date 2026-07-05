package com.saksham4106.variable;

import java.util.ArrayList;
import java.util.List;

public class VariableStateMap {

    private final List<VariableState> variableStates;

    public VariableStateMap(){
        variableStates = new ArrayList<>();
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

}
