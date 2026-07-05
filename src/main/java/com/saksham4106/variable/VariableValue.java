package com.saksham4106.variable;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.saksham4106.variable.values.CollectionValue;
import com.saksham4106.variable.values.MapValue;
import com.saksham4106.variable.values.ObjectValue;
import com.saksham4106.variable.values.PrimitiveVarValue;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)

@JsonSubTypes({
        @JsonSubTypes.Type(value = PrimitiveVarValue.class, name = "PRIMITIVE"),
        @JsonSubTypes.Type(value = ObjectValue.class, name = "OBJECT"),
        @JsonSubTypes.Type(value = MapValue.class, name = "MAP"),
        @JsonSubTypes.Type(value = CollectionValue.class, name = "COLLECTION"),
})

public abstract class VariableValue {
    private final VariableType varType;
    private final String jvmType;

    public VariableValue(VariableType varType, String jvmType){
        this.varType = varType;
        this.jvmType = jvmType;
    }

    public VariableType getVarType() {
        return varType;
    }

    public String getJvmType() {
        return jvmType;
    }
}
