package com.saksham4106;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Parser {

    public String load(String path) {
        Path filePath = Paths.get(path);

        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = LexicalPreservingPrinter.setup(StaticJavaParser.parse(content));

            List<MethodCallExpr> matches = cu.findAll(MethodCallExpr.class).stream()
                    .filter(mce -> {
                        String name = mce.getNameAsString();
                        String scope = mce.getScope().map(Object::toString).orElse("");

                        return (name.equals("of") && scope.matches("List|Set|Map"))
                                || (name.equals("asList") && scope.equals("Arrays"));

                    }).toList();

            for (MethodCallExpr mce : matches) {
                String scope = mce.getScope().get().toString();
                String wrapper = switch (scope) {
                    case "List", "Arrays" -> "ArrayList";
                    case "Set" -> "HashSet";
                    case "Map" -> "HashMap";
                    default -> throw new IllegalStateException("Invalid scope: " + scope);
                };
                mce.replace(StaticJavaParser.parseExpression("new " + wrapper + "<>(" + mce + ")"));
            }

            String transformed = LexicalPreservingPrinter.print(cu);
            Path outputDir = filePath.getParent().resolve("transformed");
            Files.createDirectories(outputDir);
            Path outputPath = outputDir.resolve(filePath.getFileName());

            Files.writeString(outputPath, transformed);
            return outputPath.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
