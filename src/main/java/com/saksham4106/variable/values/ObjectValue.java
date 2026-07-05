package com.saksham4106.variable.values;

import com.saksham4106.variable.VariableType;
import com.saksham4106.variable.VariableValue;

import java.util.List;

public class ObjectValue extends VariableValue {

    private final List<ObjectField> objectFields;

    public ObjectValue(String jvmType, List<ObjectField> objectFields) {
        super(VariableType.OBJECT, jvmType);
        this.objectFields = objectFields;
    }

    public List<ObjectField> getObjectFields() {
        return objectFields;
    }

    public record ObjectField(String fieldName, VariableValue value) {
    }
}
