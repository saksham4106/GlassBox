package com.saksham4106;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean runFromSource = false;

        File file;
        String path;

        if(runFromSource){
            String source = "";
            path = "code/tmp/Main.java";

            file = new File(path);

            if(file.createNewFile()){
                FileWriter fw = new FileWriter(file);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(source);
            }
            file.deleteOnExit();
        }else{
            path = "code/Solution.java";
            file = new File(path);
        }

        Compiler compiler = new Compiler();
        if(compiler.compile(path)){

            Debugger debugger = new Debugger(path);
            debugger.launch();

        }else{
            System.out.println("Compiler failed");
        }

        compiler.exit();



    }
}