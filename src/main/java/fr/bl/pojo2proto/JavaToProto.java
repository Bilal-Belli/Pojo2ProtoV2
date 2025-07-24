package fr.bl.pojo2proto;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

public class JavaToProto {
	// constants for message string builder
    private static final String OPEN_BLOCK = "{";
    private static final String CLOSE_BLOCK = "}";
    private static final String MESSAGE = "message";
    private static final String ENUM = "enum";
    private static final String NEWLINE = "\n";
    private static final String TAB = "\t";
    private static final String SPACE = " ";
    private static final String OPTIONAL = "optional";
    private static final String REPEATED = "repeated";
    private static final String LINE_END = ";";

    private StringBuilder builder;
    private Stack<Class<?>> classStack = new Stack<>();
    private Map<Class<?>, String> typeMap = getPrimitivesMap();
    private Set<Class<?>> processedClasses = new HashSet<>();
    private int tabDepth = 0;
    
    // Set to track types that should be skipped
    private Set<Class<?>> skipTypes = getSkipTypesSet();

    // this constructor to process with one class
    public JavaToProto(Class<?> classToProcess) {
        if (classToProcess == null) {
            throw new RuntimeException("Null class provided for processing");
        }
        classStack.push(classToProcess);
    }
    
    // this constructor to process a set of classes
    public JavaToProto(Set<Class<?>> classesToProcess) {
        if (classesToProcess == null || classesToProcess.isEmpty()) {
            throw new RuntimeException("NUll or Empty class set provided for processing");
        }
        // Add all classes to the stack
        for (Class<?> clazz : classesToProcess) {
            classStack.push(clazz);
        }
    }

    private String getTabs() {
        return new String(new char[tabDepth]).replace("\0", TAB);
    }

    private Map<Class<?>, String> getPrimitivesMap() {
        Map<Class<?>, String> results = new HashMap<>();
        results.put(double.class, "double");
        results.put(float.class, "float");
        results.put(int.class, "sint32");
        results.put(long.class, "sint64");
        results.put(boolean.class, "bool");
        results.put(Double.class, "double");
        results.put(Float.class, "float");
        results.put(Integer.class, "sint32");
        results.put(Long.class, "sint64");
        results.put(Boolean.class, "bool");
        results.put(String.class, "string");
        results.put(byte.class, "bytes");
        results.put(Byte.class, "bytes");
        results.put(short.class, "sint32");
        results.put(Short.class, "sint32");
        results.put(Date.class, "sint64"); // Treating Date as sint64 (Long)
        return results;
    }
    
    private Set<Class<?>> getSkipTypesSet() {
        Set<Class<?>> skipSet = new HashSet<>();
        skipSet.add(Class.class);
        skipSet.add(Object.class);
        return skipSet;
    }

    private void processField(String modifier, String type, String name, int index) {
      builder.append(getTabs()).append(modifier).append(SPACE).append(type).append(SPACE).append(name).append(SPACE).append("=").append(SPACE).append(index).append(LINE_END).append(NEWLINE);
    }

    private void generateProtoFile() {
        builder = new StringBuilder();
        
        // File Header
        builder.append("syntax = \"proto3\";\n" +
                "import \"protogen/options.proto\";\n" +
                "import \"google/protobuf/wrappers.proto\";\n" +
                "option java_package = \"original.class.package.here\";\n" + //consider removing it (for jar) or adjusting it (for source code execution)
                "option optimize_for = SPEED;\n" +
                "option (protogen.enable) = true;\n" +
                "option java_multiple_files = true;\n");
        
        // Process all classes
        processAllClasses();
        builder.append("\n");
    }
    
    private Set<Class<?>> findEnumClasses(Class<?> clazz) {
        Set<Class<?>> enums = new HashSet<>();
        for (Field field : getAllFields(clazz)) {
            Class<?> fieldType = field.getType();
            if (fieldType.isEnum()) {
                enums.add(fieldType);
            }
            // Handle complex use cases of enums (generics)
            if (Collection.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        Class<?> componentClass = (Class<?>) typeArgs[0];
                        if (componentClass.isEnum()) {
                            enums.add(componentClass);
                        }
                    }
                }
            }
        }
        return enums;
    }
    
    private void processAllClasses() {
        Queue<Class<?>> classesToProcess = new LinkedList<>();
        Set<Class<?>> allEnums = new HashSet<>();
        
        // Add all initial classes from the stack
        while (!classStack.isEmpty()) {
            classesToProcess.add(classStack.pop());
        }
        
        // First pass: collect all enum from all classes
        Queue<Class<?>> tempQueue = new LinkedList<>(classesToProcess);
        Set<Class<?>> visitedForEnums = new HashSet<>();
        
        while (!tempQueue.isEmpty()) {
            Class<?> currentClass = tempQueue.poll();
            
            if (visitedForEnums.contains(currentClass) || skipTypes.contains(currentClass)) {
                continue;
            }
            visitedForEnums.add(currentClass);
            
            // Collect enums from this class
            allEnums.addAll(findEnumClasses(currentClass));
            
            // Find referenced classes to also check for enums
            Set<Class<?>> referencedClasses = findReferencedClasses(currentClass);
            for (Class<?> referenced : referencedClasses) {
                if (!visitedForEnums.contains(referenced) && !referenced.isPrimitive() && !skipTypes.contains(referenced)) {
                    tempQueue.add(referenced);
                }
            }
        }
        
        // Process all enums first
        for (Class<?> enumClass : allEnums) {
            if (!processedClasses.contains(enumClass)) {
                processEnum(enumClass);
            }
        }
        
        // Then process all regular classes
        while (!classesToProcess.isEmpty()) {
            Class<?> currentClass = classesToProcess.poll();
            
            // Skip if already processed, is enum, or in skip list
            if (processedClasses.contains(currentClass) || currentClass.isEnum() || skipTypes.contains(currentClass)) {
                continue;
            }
            
            // Process the current class
            buildMessageForClass(currentClass);
            
            // Find all referenced classes that need to be processed
            Set<Class<?>> referencedClasses = findReferencedClasses(currentClass);
            for (Class<?> referenced : referencedClasses) {
                if (!processedClasses.contains(referenced) && !referenced.isPrimitive() && 
                    !referenced.isEnum() && !skipTypes.contains(referenced)) {
                    classesToProcess.add(referenced);
                }
            }
        }
    }
    
    private Set<Class<?>> findReferencedClasses(Class<?> clazz) {
        Set<Class<?>> referenced = new HashSet<>();
        
        // Skip primitive types, interfaces, abstract classes, and skip-listed classes
        if (clazz.isPrimitive() || clazz.isInterface() || 
            Modifier.isAbstract(clazz.getModifiers()) || 
            skipTypes.contains(clazz)) {
            return referenced;
        }
        
        // Process all fields to find referenced classes
        for (Field field : getAllFields(clazz)) {
            Class<?> fieldType = field.getType();
            
            // Skip static, transient fields, and skip-listed types
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || 
                skipTypes.contains(fieldType)) {
                continue;
            }
            
            // Handle collections
            if (Collection.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                        Class<?> componentClass = (Class<?>) typeArgs[0];
                        if (!skipTypes.contains(componentClass)) {
                            referenced.add(componentClass);
                        }
                    }
                }
                continue;
            }
            
            // Handle maps
            if (Map.class.isAssignableFrom(fieldType)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericType;
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length > 1) {
                        if (typeArgs[0] instanceof Class && !skipTypes.contains((Class<?>) typeArgs[0])) {
                            referenced.add((Class<?>) typeArgs[0]);
                        }
                        if (typeArgs[1] instanceof Class && !skipTypes.contains((Class<?>) typeArgs[1])) {
                            referenced.add((Class<?>) typeArgs[1]);
                        }
                    }
                }
                continue;
            }
            
            // Handle arrays
            if (fieldType.isArray()) {
                Class<?> componentType = fieldType.getComponentType();
                if (!skipTypes.contains(componentType)) {
                    referenced.add(componentType);
                }
                continue;
            }
            
            // Add regular field types
            if (!fieldType.isPrimitive() && !typeMap.containsKey(fieldType) && !skipTypes.contains(fieldType)) {
                referenced.add(fieldType);
            }
        }
        
        // Add inner classes
        for (Class<?> innerClass : clazz.getDeclaredClasses()) {
            if (!Modifier.isPrivate(innerClass.getModifiers()) && !skipTypes.contains(innerClass)) {
                referenced.add(innerClass);
            }
        }
        
        return referenced;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> allFields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        // Get fields from all super-classes
        while (currentClass != null && !currentClass.equals(Object.class)) {
            // Get declared fields including private ones
            Field[] declaredFields = currentClass.getDeclaredFields();
            
            // Add all non-synthetic fields
            for (Field field : declaredFields) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                    allFields.add(field);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        
        return allFields;
    }

    private String buildMessageForClass(Class<?> clazz) {
        if (clazz == null || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || 
            skipTypes.contains(clazz)) {
            return null;
        }

        if (processedClasses.contains(clazz)) {
            return getMessageTypeName(clazz);
        }

        processedClasses.add(clazz);
        
        String messageName = "Grpc" + clazz.getSimpleName();
        typeMap.put(clazz, messageName);

        builder.append("\n").append(MESSAGE).append(SPACE).append(messageName).append(SPACE).append(OPEN_BLOCK).append(NEWLINE);

        tabDepth++;
        processFieldsForClass(clazz);
        tabDepth--;

        builder.append(CLOSE_BLOCK).append(NEWLINE);

        return messageName;
    }

    private void processFieldsForClass(Class<?> clazz) {
        List<Field> fields = getAllFields(clazz);
        int fieldIndex = 1;
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            Class<?> fieldType = field.getType();
            
            // Skip Predefined Class Types (Class, Object) fields
            if (skipTypes.contains(fieldType)) {
                continue;
            }
            
            // Handle Primitive types including Date
            if (typeMap.containsKey(fieldType)) {
                processField(OPTIONAL, typeMap.get(fieldType), field.getName(), fieldIndex++);
                continue;
            }

            if (Collection.class.isAssignableFrom(fieldType)) {
                processCollectionField(field, fieldIndex++);
                continue;
            }

            if (Map.class.isAssignableFrom(fieldType)) {
                processMapField(field, fieldIndex++);
                continue;
            }

            if (fieldType.isArray()) {
                processArrayField(field, fieldIndex++);
                continue;
            }
            
            // For complex types that are not yet processed, we'll process them later
            // Just reference them here
            String typeReference = getMessageTypeName(fieldType);
            processField(OPTIONAL, typeReference, field.getName(), fieldIndex++);
        }
    }
    
    private String getMessageTypeName(Class<?> clazz) {
        if (typeMap.containsKey(clazz)) {
            return typeMap.get(clazz);
        }
        
        // For classes not yet in typeMap but will be processed
        if (clazz.isEnum()) {
            return "Grpc" + clazz.getSimpleName();
        }
        return "Grpc" + clazz.getSimpleName();
    }

    private void processCollectionField(Field field, int index) {
        Class<?> componentType = null;
        Type genericType = field.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

            if (actualTypeArguments.length == 1) {
                Type actualTypeArgument = actualTypeArguments[0];

                if (actualTypeArgument instanceof Class) {
                    componentType = (Class<?>) actualTypeArgument;
                    
                    // Skip collections of Class or Object
                    if (skipTypes.contains(componentType)) {
                        processField(REPEATED, "google.protobuf.Any", field.getName(), index);
                        return;
                    }
                } else if (actualTypeArgument instanceof WildcardType) {
                    processField(REPEATED, "google.protobuf.Any", field.getName(), index);
                    return;
                } else {
                    System.err.println("Unsupported generic type: " + actualTypeArgument.getTypeName());
                }
            } else {
                System.err.println("Unsupported number of generic type arguments: " + actualTypeArguments.length);
            }
        }

        // Process the determined componentType (if any)
        if (componentType != null) {
            String typeName = getProtoType(componentType);
            processField(REPEATED, typeName, field.getName(), index);
        }
    }
    
    private void processMapField(Field field, int index) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType)) {
            return;
        }

        ParameterizedType paramType = (ParameterizedType) genericType;
        Type[] typeArgs = paramType.getActualTypeArguments();
        Class<?> keyType = (Class<?>) typeArgs[0];
        Class<?> valueType = (Class<?>) typeArgs[1];
        
        // Handle special cases
        if (skipTypes.contains(keyType) || skipTypes.contains(valueType)) {
            processField(OPTIONAL, "google.protobuf.Any", field.getName(), index);
            return;
        }
        
        // Handle Date types in maps
        String keyTypeName = Date.class.isAssignableFrom(keyType) ? "sint64" : getProtoType(keyType);
        String valueTypeName = Date.class.isAssignableFrom(valueType) ? "sint64" : getProtoType(valueType);

        // In proto3, maps are represented as: map<key_type, value_type> field_name = field_number;
        builder.append(getTabs())
              .append("map<").append(keyTypeName).append(", ").append(valueTypeName).append(">")
              .append(SPACE).append(field.getName()).append(SPACE)
              .append("=").append(SPACE).append(index).append(LINE_END).append(NEWLINE);
    }

    private void processArrayField(Field field, int index) {
        Class<?> componentType = field.getType().getComponentType();
        
        // Skip arrays of Class or Object
        if (skipTypes.contains(componentType)) {
            processField(REPEATED, "google.protobuf.Any", field.getName(), index);
            return;
        }
        
        String typeName = getProtoType(componentType);
        
        // For multi-dimensional arrays, create a separate message type
        if (componentType.isArray()) {
            // Create a message type for the array rows
            String rowMessageName = "Array_" + field.getDeclaringClass().getSimpleName() + "_" + field.getName();
            createArrayRowMessage(rowMessageName, componentType);
            
            // Use the created message type as a repeated field
            processField(REPEATED, rowMessageName, field.getName(), index);
        } else {
            // Simple array - just use repeated
            processField(REPEATED, typeName, field.getName(), index);
        }
    }
    
    private void createArrayRowMessage(String messageName, Class<?> arrayType) {
        // Skip if already processed
        if (processedClasses.contains(arrayType)) {
            return;
        }
        
        Class<?> componentType = arrayType.getComponentType();
        
        // Skip arrays of Class or Object
        if (skipTypes.contains(componentType)) {
            return;
        }
        
        // Create a message type for the array rows
        builder.append("\n").append(MESSAGE).append(SPACE).append(messageName).append(SPACE)
               .append(OPEN_BLOCK).append(NEWLINE);
        
        tabDepth++;
        
        if (componentType.isArray()) {
            // For nested arrays, create another message type
            String nestedRowMessageName = messageName + "_Row";
            processField(REPEATED, nestedRowMessageName, "items", 1);
            
            // Process the nested array type
            createArrayRowMessage(nestedRowMessageName, componentType);
        } else {
            // For simple arrays, use the component type
            String typeName = getProtoType(componentType);
            processField(REPEATED, typeName, "items", 1);
        }
        
        tabDepth--;
        builder.append(CLOSE_BLOCK).append(NEWLINE);
        
        // Mark as processed
        processedClasses.add(arrayType);
    }

    private String getProtoType(Class<?> type) {
        // Handle Date types
        if (Date.class.isAssignableFrom(type)) {
            return "sint64";
        }
        
        // Handle Class and Object types
        if (skipTypes.contains(type)) {
            return "google.protobuf.Any";
        }
        
        if (typeMap.containsKey(type)) {
            return typeMap.get(type);
        }

        if (type.isPrimitive()) {
            // This should be covered by typeMap but just in case
            return "sint32"; // Default for unknown primitives
        }
        
        // For complex types, return the appropriate name reference
        return getMessageTypeName(type);
    }

    private void processEnum(Class<?> enumType) {
        if (processedClasses.contains(enumType)) {
            return;
        }

        processedClasses.add(enumType);
        typeMap.put(enumType, "Grpc"+enumType.getSimpleName());

        builder.append("\n").append(ENUM).append(SPACE).append("Grpc"+enumType.getSimpleName()).append(SPACE)
               .append(OPEN_BLOCK).append(NEWLINE);

        tabDepth++;
        Object[] enumConstants = enumType.getEnumConstants();
        for (int i = 0; i < enumConstants.length; i++) {
            builder.append(getTabs()).append(enumConstants[i].toString()).append(" = ").append(i).append(LINE_END).append(NEWLINE);
        }
        tabDepth--;

        builder.append(CLOSE_BLOCK).append(NEWLINE);
    }

    @Override
    public String toString() {
        if (builder == null) {
            generateProtoFile();
        }
        return builder.toString();
    }
}