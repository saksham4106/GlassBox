package com.saksham4106.variable.values;

import com.saksham4106.variable.VariableType;
import com.saksham4106.variable.VariableValue;

import java.util.List;

public class PrimitiveArrayValue extends VariableValue {

    private final List<String> elements;
    public PrimitiveArrayValue(String jvmType, List<String> elements) {
        super(VariableType.COLLECTION, jvmType);
        this.elements = elements;
    }

    public List<String> getElements() {
        return elements;
    }
}
