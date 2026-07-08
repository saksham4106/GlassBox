package com.saksham4106;

import com.saksham4106.serialization.Serializer;
import com.saksham4106.variable.VariableValue;
import com.saksham4106.variable.values.*;
import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import org.apache.commons.io.FilenameUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

public class Debugger {

    record VarKey(String frameID, LocalVariable var) {}

    private final String clazz;
    private final String classPath;
    private long counter = 0;


    Map<VarKey, Integer> hashes = new HashMap<>();
    Map<Integer, String> frameIdMap = new HashMap<>();
    private VirtualMachine vm;

    public Debugger(String path) {
        clazz = FilenameUtils.getBaseName(path);
        classPath = Path.of(path).getParent().toString();
    }

    public void launch() throws Exception{

        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();

        Map<String, Connector.Argument> args =  connector.defaultArguments();

        args.get("main").setValue(clazz);
        args.get("options").setValue("-cp " + classPath);

        vm = connector.launch(args);

        this.setupIOStream();

        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(clazz);
        classPrepareRequest.enable();

        MethodEntryRequest methodEntryRequest = vm.eventRequestManager().createMethodEntryRequest();
        methodEntryRequest.addClassFilter(clazz);
        methodEntryRequest.enable();

        MethodExitRequest methodExitRequest = vm.eventRequestManager().createMethodExitRequest();
        methodExitRequest.addClassFilter(clazz);
        methodExitRequest.enable();

        EventQueue queue = vm.eventQueue();

        while (true) {
            if(!eventLoop(queue)) return;

        }
    }
    String currentFrameId = null;
    private boolean eventLoop(EventQueue queue) throws Exception{
        EventSet events = queue.remove();

        for(Event event : events) {

            if(event instanceof MethodEntryEvent me){
                ThreadReference thread = me.thread();
                Method currMethod = me.method();
                StackFrame frame = thread.frame(0);

                String parentCall = null;

                if(thread.frameCount() > 1) parentCall = frameIdMap.get(thread.frameCount() - 1);

                String currCall = currMethod.name() + "_" + counter++;
                currentFrameId = currCall;

                frameIdMap.put(thread.frameCount(), currCall);

                ObjectNode argsNode = Serializer.mapper.createObjectNode();

                for(LocalVariable arg: currMethod.arguments()){
                    Value val = frame.getValue(arg);
                    VariableValue argValue = parseValue(val, 1, thread);

                    VarKey key = new VarKey(currentFrameId, arg);
                    JsonNode varNode = Serializer.mapper.valueToTree(argValue);
                    argsNode.set(arg.name(), varNode);

                    hashes.put(key, varNode.hashCode());
                }

                ObjectNode pushCall = Serializer.mapper.createObjectNode();
                pushCall.put("type", "push_frame");
                pushCall.put("frame", currCall);
                pushCall.put("parent", parentCall);
                pushCall.set("args", argsNode);

                System.out.println(Serializer.serialize(pushCall));
            }


            if(event instanceof StepEvent se){
                Location location = se.location();
                ThreadReference thread = se.thread();
                StackFrame frame = thread.frame(0);

                List<LocalVariable> visibleVariables = frame.visibleVariables();
                Map<LocalVariable, Value> visibleVals = frame.getValues(visibleVariables);

                ObjectNode varStateNode = Serializer.mapper.createObjectNode();

                for (Map.Entry<LocalVariable, Value> entry : visibleVals.entrySet()) {
                    LocalVariable var = entry.getKey();
                    Value val = entry.getValue();

                    if(val != null) {
                        VariableValue parsedValue = parseValue(val, 1, thread);
                        JsonNode node = Serializer.mapper.valueToTree(parsedValue);
                        int newHash = node.hashCode();

                        VarKey key = new VarKey(currentFrameId, var);
                        Integer oldHash = hashes.get(key);
                        if(oldHash == null || oldHash != newHash) {
                            varStateNode.set(var.name(), node);
                            hashes.put(key, newHash);
                        }
                    }
                }

                ObjectNode payload = Serializer.mapper.createObjectNode();
                payload.put("line", location.lineNumber() - 1);
                payload.put("frameId", currentFrameId);
                payload.set("varState", varStateNode);

                System.out.println(Serializer.serialize(payload));
            }

            if(event instanceof MethodExitEvent me){
                ThreadReference thread = me.thread();
                int currentDepth = thread.frameCount();

                String callId = frameIdMap.get(currentDepth);

                if(currentDepth > 0){
                    currentFrameId = frameIdMap.get(currentDepth - 1);
                }

                if(callId != null){
                    Value returnVal = me.returnValue();
                    VariableValue parsedReturn = parseValue(returnVal, 1, thread);

                    ObjectNode returnNode = Serializer.mapper.createObjectNode();
                    returnNode.put("type", "pop");
                    returnNode.put("frame", callId);
                    returnNode.set("return",  Serializer.mapper.valueToTree(parsedReturn));

                    System.out.println(Serializer.serialize(returnNode));
                }
            }


            if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) return false;

            if(event instanceof ClassPrepareEvent cpe) setBreakpointAtMain(cpe);

            if(event instanceof BreakpointEvent bpe){
                ThreadReference thread = bpe.thread();
                enableStep(vm, thread);
            }
        }


        events.resume();
        return true;
    }



    int maxDepth = 5;
    int lengthLimit = 15;
    Map<String,String> boxed = Map.of(
            "java.lang.Integer", "int",
            "java.lang.Long", "long",
            "java.lang.Double", "double",
            "java.lang.Float", "float",
            "java.lang.Character", "char",
            "java.lang.Boolean", "boolean");


    Set<String> primitiveArrays = Set.of(
            "int", "long", "short", "byte", "char", "boolean", "float", "double", "java.lang.String"
    );

    Map<String, List<String>> parseMap = new HashMap<>();


    public VariableValue parseValue(Value val, int depth, ThreadReference thread) {
        if(val == null || val.toString().equals("null")) return new PrimitiveVarValue("null", "null");

        String type = val.type().name();


        if(depth >  maxDepth) return new PrimitiveVarValue("string", "Max Depth Reached");

        if(val instanceof PrimitiveValue || val instanceof StringReference){
            return new PrimitiveVarValue(type, val.toString());
        }

        if(val instanceof ArrayReference arrayRef){
            ArrayType arrayType = (ArrayType) arrayRef.type();
            String arrComponentName = arrayType.componentTypeName();

            int length = Math.min(lengthLimit, arrayRef.length());
            List<Value> values = arrayRef.getValues(0, length);
            List<VariableValue> li = values.stream().map(v -> parseValue(v, depth + 1, thread)).toList();


            if(primitiveArrays.contains(arrComponentName)){
                List<String> elements = values.stream().map(v -> v != null ? v.toString() : "null").toList();
                return new PrimitiveArrayValue(type, elements);
            }

            return new CollectionValue(type, li);
        }

        if(val instanceof ObjectReference obj){

            if(boxed.containsKey(type)){
                Field valueField =  obj.referenceType().fieldByName("value");
                if(valueField != null){
                    return new PrimitiveVarValue(boxed.get(type), obj.getValue(valueField).toString());
                }
            }else{
                if(type.equals("java.util.ArrayList")){
                    Field elementsField = obj.referenceType().fieldByName("elementData");
                    Field sizeField = obj.referenceType().fieldByName("size");

                    int size = Math.min(lengthLimit, ((PrimitiveValue) obj.getValue(sizeField)).intValue());
                    ArrayReference elementArray = (ArrayReference) obj.getValue(elementsField);

                    List<Value> elements = elementArray.getValues(0, size);
                    List<VariableValue> values = elements.stream().map(v -> parseValue(v, depth + 1, thread)).toList();
                    return new CollectionValue(type, values);

                }else if(type.equals("java.util.HashMap")){
                    try {
                        Method entrySetMethod = obj.referenceType().allMethods().stream()
                                .filter(m -> m.name().equals("entrySet") && m.argumentTypeNames().isEmpty())
                                .findFirst()
                                .orElse(null);

                        if (entrySetMethod != null) {
                            ObjectReference entrySetRef = (ObjectReference) obj.invokeMethod(thread, entrySetMethod, new ArrayList<>(), ObjectReference.INVOKE_SINGLE_THREADED);

                            Method toArray = entrySetRef.referenceType().allMethods().stream()
                                    .filter(m -> m.name().equals("toArray") && m.argumentTypeNames().isEmpty())
                                    .findFirst()
                                    .orElseThrow(() -> new NoSuchMethodException("toArray() not found"));

                            ArrayReference entriesArray = (ArrayReference) entrySetRef.invokeMethod(thread, toArray, new ArrayList<>(), ObjectReference.INVOKE_SINGLE_THREADED);

                            int size = entriesArray.length();
                            List<MapValue.MapEntry> entries = new ArrayList<>();

                            for (int i = 0; i < size; i++) {
                                ObjectReference entryObj = (ObjectReference) entriesArray.getValue(i);
                                if (entryObj == null) continue;

                                Method getKeyMethod = entryObj.referenceType().allMethods().stream()
                                        .filter(m -> m.name().equals("getKey")).findFirst().get();
                                Method getValueMethod = entryObj.referenceType().allMethods().stream()
                                        .filter(m -> m.name().equals("getValue")).findFirst().get();

                                Value keyVal = entryObj.invokeMethod(thread, getKeyMethod, new ArrayList<>(), ObjectReference.INVOKE_SINGLE_THREADED);
                                Value valueVal = entryObj.invokeMethod(thread, getValueMethod, new ArrayList<>(), ObjectReference.INVOKE_SINGLE_THREADED);

                                VariableValue keyValue = parseValue(keyVal, depth + 1, thread);
                                VariableValue valueValue = parseValue(valueVal, depth + 1, thread);
                                entries.add(new MapValue.MapEntry(keyValue, valueValue));
                            }

                            // potentially expensive
                            entries.sort(Comparator.comparing(a -> Serializer.serialize(a.keyValue())));

                            return new MapValue(type, entries);
                        } else {
                            return new MapValue(type, new ArrayList<>());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                if(!type.contains("java")){
                    List<String> fields = getObjectFields(obj);
                    if(!fields.isEmpty()){
                        List<ObjectValue.ObjectField> out = new ArrayList<>();
                        for(String field : fields){
                            Field targetField = obj.referenceType().fieldByName(field);
                            out.add(new ObjectValue.ObjectField(
                                    field,
                                    parseValue(obj.getValue(targetField), depth + 1, thread)
                            ));

                        }
                        return new ObjectValue(type, out);
                    }
                }

            }
        }

        return new PrimitiveVarValue(type, val.toString());

    }

    private List<String> getObjectFields(ObjectReference obj){
        String className = obj.referenceType().name();
        if(parseMap.containsKey(className)){
            return parseMap.get(className);
        }

        List<Field> fields = obj.referenceType().fields();
        List<String> sortedFieldNames = fields.stream()
                .map(Field::name)
                .sorted()
                .toList();

        parseMap.put(className, sortedFieldNames);
        return sortedFieldNames;

    }

    public void setupIOStream(){
        if(vm == null) return;

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(vm.process().getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Target System.out] " + line);
                }
            } catch (IOException ignored) {
            }
        }, "Target-Stdout-Redirector").start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(vm.process().getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Target System.err] " + line);
                }
            } catch (IOException ignored) {
            }
        }, "Target-Stderr-Redirector").start();
    }

    public void enableStep(VirtualMachine vm, ThreadReference thread) {
        EventRequestManager eventRequestManager = vm.eventRequestManager();

        for (StepRequest sr : eventRequestManager.stepRequests()) {
            if (sr.thread().equals(thread)) {
                eventRequestManager.deleteEventRequest(sr);
            }
        }

        StepRequest stepRequest = eventRequestManager.createStepRequest(thread,
                StepRequest.STEP_LINE, StepRequest.STEP_INTO);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("jdk.*");
        stepRequest.addClassExclusionFilter("sun.*");


        stepRequest.enable();
    }

    private void setBreakpointAtMain(ClassPrepareEvent cpe){
        ReferenceType referenceType = cpe.referenceType();

        List<Method> methods = referenceType.methodsByName("main");
        Method main =  methods.getFirst();
        Location loc = main.locationOfCodeIndex(0);

        EventRequestManager eventRequestManager = vm.eventRequestManager();
        BreakpointRequest bpRequest = eventRequestManager.createBreakpointRequest(loc);
        bpRequest.enable();

    }
}
