package com.saksham4106.serialization;

import com.saksham4106.variable.VariableStateMap;
import tools.jackson.databind.ObjectMapper;

public class Serializer {

    public static void start(VariableStateMap map){
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        System.out.println(json);
    }
}
