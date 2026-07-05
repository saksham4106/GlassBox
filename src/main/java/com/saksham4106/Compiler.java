package com.saksham4106;


import javax.tools.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class Compiler {

    private final JavaCompiler javaCompiler;
    private final DiagnosticCollector<JavaFileObject> diagnosticCollector;
    private final StandardJavaFileManager fileManager;

    public Compiler() {
        javaCompiler = ToolProvider.getSystemJavaCompiler();
        diagnosticCollector = new DiagnosticCollector<>();
        fileManager = javaCompiler.getStandardFileManager(diagnosticCollector, null, null);
    }

    public boolean compile(String javaFilePath) {

        Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromStrings(
                List.of(javaFilePath)
        );

        Iterable<String> options = List.of(
                "-g"
        );

        boolean code = javaCompiler.
                getTask(null, fileManager, diagnosticCollector, options, null, sources).call();


        for(Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics()) {
            System.out.println(diagnostic.getMessage(null));
        }

        return code;
    }

    public void exit() throws Exception {
        fileManager.close();
    }
}
