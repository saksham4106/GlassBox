package com.saksham4106.variable.values;

import com.saksham4106.variable.VariableType;
import com.saksham4106.variable.VariableValue;

public class PrimitiveVarValue extends VariableValue {

    private final String data;

    public PrimitiveVarValue(String jvmType, String data) {
        super(VariableType.PRIMITIVE, jvmType);
        this.data = data;
    }

    public String getData() {
        return data;
    }
}
