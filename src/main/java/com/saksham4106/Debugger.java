package com.saksham4106;

import com.saksham4106.serialization.Serializer;
import com.saksham4106.variable.VariableState;
import com.saksham4106.variable.VariableStateMap;
import com.saksham4106.variable.VariableValue;
import com.saksham4106.variable.values.CollectionValue;
import com.saksham4106.variable.values.MapValue;
import com.saksham4106.variable.values.ObjectValue;
import com.saksham4106.variable.values.PrimitiveVarValue;
import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Debugger {

    private final String clazz;
    private final String classPath;

    private long counter = 0;

    Map<Integer, String> frameIdMap = new HashMap<>();
    Map<String, List<VariableState>> functionArgs = new HashMap<>();

    List<Pair<String, String>> callStack = new ArrayList<>();
    List<Pair<String, VariableValue>> returnStack = new ArrayList<>();

    private VirtualMachine vm;
    public Debugger(String path){
        clazz = FilenameUtils.getBaseName(path);
        classPath = Path.of(path).getParent().toString();
    }

    public void launch() throws Exception{

        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();

        Map<String, Connector.Argument> args =  connector.defaultArguments();

        args.get("main").setValue(clazz);
        args.get("options").setValue("-cp " + classPath);

        vm = connector.launch(args);
//        vm.resume();

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

    private boolean eventLoop(EventQueue queue) throws Exception{

        EventSet events = queue.remove();
        for(Event event : events) {
            if(event instanceof VMStartEvent){
                System.out.println("Virtual Machine started");
            }

            if(event instanceof ClassPrepareEvent cpe){
                ReferenceType referenceType = cpe.referenceType();

                List<Method> methods = referenceType.methodsByName("main");
                Method main =  methods.get(0);
                Location loc = main.locationOfCodeIndex(0);

                EventRequestManager eventRequestManager = vm.eventRequestManager();
                BreakpointRequest bpRequest = eventRequestManager.createBreakpointRequest(loc);
                bpRequest.enable();

            }

            if(event instanceof MethodEntryEvent me){
                ThreadReference thread = me.thread();
                Method currMethod = me.method();


                String parentCall = null;

                if(thread.frameCount() > 1){
                    StackFrame frame = thread.frame(1);
                    Method parentCallMethod = frame.location().method();

                    if(parentCallMethod.name().equals(currMethod.name())){
                        parentCall = frameIdMap.get(thread.frameCount() - 1);
                    }
                }

                String currCall = currMethod.name() + "_" + counter++;
                StackFrame frame = thread.frame(0);
                frameIdMap.put(thread.frameCount(), currCall);

                for(LocalVariable arg: currMethod.arguments()){
                    Value val = frame.getValue(arg);
                    functionArgs.computeIfAbsent(currCall, k -> new ArrayList<>()).add(
                            new VariableState(arg.name(), parseValue(val, 1, thread)));
                }

                callStack.add(new Pair<>(parentCall, currCall));

            }

            if(event instanceof MethodExitEvent me){
                ThreadReference thread = me.thread();
                int currentDepth = thread.frameCount();

                String callId = frameIdMap.get(currentDepth);

                if(callId != null){
                    Value returnVal = me.returnValue();
                    VariableValue parsedReturn = parseValue(returnVal, 1, thread);
                    returnStack.add(new Pair<>(callId, parsedReturn));
                }
            }

            if(event instanceof BreakpointEvent bpe){
                System.out.println("set breakpoint at " + bpe.location());
                ThreadReference thread = bpe.thread();
                enableStep(vm, thread);
            }


            if(event instanceof StepEvent se){
                Location location = se.location();
                ThreadReference thread = se.thread();

                StackFrame frame = thread.frame(0);
                List<LocalVariable> vars = frame.visibleVariables();

                Map<LocalVariable, Value> vals = frame.getValues(vars);

                VariableStateMap varState = new VariableStateMap();

                for (Map.Entry<LocalVariable, Value> entry : vals.entrySet()) {
                    LocalVariable var = entry.getKey();
                    Value val = entry.getValue();

//                    if(val instanceof PrimitiveValue priv){
//
//                        pair = Pair.of(priv.type().name(), priv.toString());
//                    }else if(val instanceof StringReference stringRef){
//
//                        pair = Pair.of(stringRef.type().name(), stringRef.toString());
//                    }else if(val instanceof ArrayReference arrayRef){
//
//                        pair = Pair.of(arrayRef.type().name(), arrayRef.getValues().toString());
//                            HAVE TO DO THIS TO STOP SYSTEM.ERR FROM GOING MAD
//                            System.out.println();
//                            System.out.println(arrayRef.type().name() + ": " + arrayRef.getValues());
//                    }else if(val instanceof ObjectReference objectRef){
//
//                        pair = evalObjectReference(objectRef, 10);
//                    }
                    if(val != null) {
                        varState.addVariable(var.name(), parseValue(val, 1, thread));
                    }

                }

                Serializer.start(varState);

//                System.out.print(location.lineNumber() + " -> ");
            }

            if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                System.out.println(functionArgs.entrySet().stream()
                        .map(entry -> entry.getKey() + " -> " + entry.getValue())
                        .collect(Collectors.joining("\n   ", "{\n   ", "\n}")));
                System.out.println(callStack);
                System.out.println(returnStack);

                return false;
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


    Map<String, List<String>> parseMap = new HashMap<>();


    public VariableValue parseValue(Value val, int depth, ThreadReference thread) {
        if(val == null || val.toString().equals("null")) return new PrimitiveVarValue("null", "null");

        String type = val.type().name();


        if(depth >  maxDepth) return new PrimitiveVarValue("string", "Max Depth Reached");

        if(val instanceof PrimitiveValue || val instanceof StringReference){
            return new PrimitiveVarValue(type, val.toString());
        }

        if(val instanceof ArrayReference arrayRef){
            int length = Math.min(lengthLimit, arrayRef.length());
            List<Value> values = arrayRef.getValues(0, length);
            List<VariableValue> li = values.stream().map(v -> parseValue(v, depth + 1, thread)).toList();
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
}
