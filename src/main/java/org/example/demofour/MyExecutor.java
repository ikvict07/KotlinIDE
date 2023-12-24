package org.example.demofour;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.util.function.Consumer;

public class MyExecutor {
    private PrintWriter processInputWriter;

    private static Process executeCommand(String[] command) {
        String[] fullCommand;
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Windows
            fullCommand = new String[command.length + 2];
            fullCommand[0] = "cmd.exe";
            fullCommand[1] = "/c";
            System.arraycopy(command, 0, fullCommand, 2, command.length);
        } else {
            // Unix or Mac
            fullCommand = new String[command.length + 1];
            fullCommand[0] = "/usr/bin/env";
            System.arraycopy(command, 0, fullCommand, 1, command.length);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
        processBuilder.redirectErrorStream(true);
        Process process = null;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return process;
    }

    private static void deleteFile(String path) throws FileSystemException {
        File fileToBeDeleted = new File(path);
        boolean result = fileToBeDeleted.delete();
        if (!result) {
            throw new FileSystemException("Could not delete temp kts file");
        }
    }

    public void sendInputToProcess(String input) {
        processInputWriter.println(input);
        processInputWriter.flush();
    }

    public void execute(Consumer<String> outputConsumer, Runnable onProgramStart, Runnable onProgramEnd) {
        Process kotlinCompileProcess = executeCommand(new String[]{"kotlinc", "-script", "TempKotlin.kts"});
        processInputWriter = new PrintWriter(kotlinCompileProcess.getOutputStream());

        new Thread(() -> {
            try {
                onProgramStart.run();
                BufferedReader reader = new BufferedReader(new InputStreamReader(kotlinCompileProcess.getInputStream(), StandardCharsets.UTF_8));
                String line;

                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    outputConsumer.accept(finalLine);
                }

                int exitCode = kotlinCompileProcess.waitFor();
                outputConsumer.accept("Exit code " + exitCode);

                onProgramEnd.run();

                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

            try {
                deleteFile("TempKotlin.kts");
            } catch (FileSystemException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}