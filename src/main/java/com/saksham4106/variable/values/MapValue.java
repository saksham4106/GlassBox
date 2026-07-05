package com.saksham4106.variable.values;


import com.saksham4106.variable.VariableType;
import com.saksham4106.variable.VariableValue;

import java.util.List;

public class MapValue extends VariableValue {
    private final List<MapEntry> mapEntryList;
    public MapValue(String jvmType, List<MapEntry> mapEntryList) {
        super(VariableType.MAP, jvmType);
        this.mapEntryList = mapEntryList;
    }

    public List<MapEntry> getMapEntryList() {
        return mapEntryList;
    }

    public record MapEntry(VariableValue keyValue, VariableValue valueValue) {
    }
}
