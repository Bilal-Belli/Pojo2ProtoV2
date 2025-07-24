package fr.bl.pojo2proto;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenerateProtoFile {
	public static List<String> allClasses;
	private static final String PROTO_OUTPUT_DIR = "src/main/proto/";

    public static void main(String[] args) {
         // Put all founded DTO classes here to be processed
    	Set<Class<?>> classesToConvert = new HashSet<>(Arrays.asList(
    		    Object.class,
    		    Integer.class
    		));
         JavaToProto jpt = new JavaToProto(classesToConvert);
         writeProtoFile("K.proto",jpt.toString());
    }

    private static void writeProtoFile(String fileName, String content) {
        File protoDir = new File(PROTO_OUTPUT_DIR);
        if (!protoDir.exists()) {
            protoDir.mkdirs();
        }
        File protoFile = new File(protoDir, fileName);
        try (FileWriter writer = new FileWriter(protoFile)) {
            writer.write(content);
        } catch (IOException e) {
            System.err.println("Error writing proto file " + fileName + ": " + e.getMessage());
        }
    }
}