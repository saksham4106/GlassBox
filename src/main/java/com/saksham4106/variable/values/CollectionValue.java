package com.saksham4106.variable.values;

import com.saksham4106.variable.VariableType;
import com.saksham4106.variable.VariableValue;

import java.util.List;

public class CollectionValue extends VariableValue {
    private final List<VariableValue> variableValueList;

    public CollectionValue(String jvmType, List<VariableValue> variableValueList) {
        super(VariableType.COLLECTION, jvmType);
        this.variableValueList = variableValueList;
    }

    public List<VariableValue> getVariableValueList() {
        return variableValueList;
    }
}
