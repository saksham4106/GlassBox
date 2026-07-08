package com.saksham4106.serialization;

import tools.jackson.databind.ObjectMapper;

public class Serializer {
    public static ObjectMapper mapper = new ObjectMapper();
    public static String serialize(Object map){
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }
}
