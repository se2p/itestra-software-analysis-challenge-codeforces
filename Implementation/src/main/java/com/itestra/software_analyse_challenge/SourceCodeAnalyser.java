package com.itestra.software_analyse_challenge;

import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SourceCodeAnalyser {

    /**
     * Your implementation
     *
     * @param input {@link Input} object.
     * @return mapping from filename -> {@link Output} object.
     */
        // TODO insert your Code here.

        // For each file put one Output object to your result map.
        // You can extend the Output object using the functions lineNumberBonus(int), if you did
        // the bonus exercise.

    public static Map<String, Output> analyse(Input input) {
        Map<String, Output> result = new HashMap<>();
        File inputDir = input.getInputDirectory();
    
        if (inputDir == null || !inputDir.exists() || !inputDir.isDirectory()) {
            System.err.println("Invalid input directory: " + inputDir);
            return result;
        }
    
        int basePathLength = inputDir.getPath().length() + 1;
        processDirectory(inputDir, result, basePathLength);
        return result;
    }
    
    private static void processDirectory(File dir, Map<String, Output> result, int basePathLength) {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                processDirectory(file, result, basePathLength);
            } else if (file.getName().endsWith(".java")) {
                int totalSloc = countSLOC(file);
                int slocWithoutGettersAndBlockComments = countSLOCWithoutGettersAndBlockComments(file);
    
                List<String> dependencies = detectDependencies(file);
    
                String relativePath = file.getAbsolutePath().substring(basePathLength);
                Output output = new Output(totalSloc, dependencies);
                output.lineNumberBonus(slocWithoutGettersAndBlockComments);
                result.put(relativePath, output);
            }
        }
    }
    
    
    private static int countSLOC(File file) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + file.getPath());
            e.printStackTrace();
        }
        return count;
    }
    
    private static int countSLOCWithoutGettersAndBlockComments(File file) {
        int count = 0;
        boolean insideBlockComment = false;
        boolean insideGetter = false;
        List<String> buffer = new ArrayList<>();
    
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
    
                // Handle block comments
                if (insideBlockComment) {
                    if (trimmed.contains("*/")) {
                        insideBlockComment = false;
                    }
                    continue;
                }
    
                if (trimmed.startsWith("/*")) {
                    insideBlockComment = true;
                    continue;
                }
    
                if (trimmed.startsWith("//") || trimmed.isEmpty()) {
                    continue;
                }
    
                buffer.add(trimmed);
    
                // Check for getter structure (assumes getter is 2-3 lines max)
                if (buffer.size() >= 2) {
                    if (isGetter(buffer)) {
                        buffer.clear();
                        insideGetter = false;
                        continue;
                    }
                    // If not a getter, count all buffered lines
                    for (String bLine : buffer) {
                        count++;
                    }
                    buffer.clear();
                }
            }
    
            // Catch remaining buffered lines if not a getter
            for (String bLine : buffer) {
                count++;
            }
    
        } catch (IOException e) {
            System.err.println("Failed to read file: " + file.getPath());
            e.printStackTrace();
        }
    
        return count;
    }
    
    private static boolean isGetter(List<String> lines) {
        if (lines.size() < 2 || lines.size() > 3) return false;
    
        String first = lines.get(0);
        String second = lines.get(1);
    
        // Check for 'public <type> getX() {'
        boolean firstMatches = first.matches("public\\s+\\w+\\s+get\\w*\\s*\\(\\s*\\)\\s*\\{?");
    
        // Check for 'return this.x;' or 'return x;'
        boolean secondMatches = second.matches("return\\s+(this\\.)?\\w+\\s*;");
    
        return firstMatches && secondMatches;
    }
    
    private static List<String> detectDependencies(File file) {
        List<String> dependencies = new ArrayList<>();
        Set<String> knownProjects = new HashSet<>(Arrays.asList("cronutils", "fig", "spark"));
    
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("import ") && !line.startsWith("import static") && !line.endsWith(".*;")) {
                    for (String project : knownProjects) {
                        if (line.contains("import " + project + ".") && !dependencies.contains(project)) {
                            dependencies.add(project);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read imports from: " + file.getAbsolutePath());
            e.printStackTrace();
        }
    
        return dependencies;
    }
    


    /**
     * INPUT - OUTPUT
     *
     * No changes below here are necessary!
     */

    public static final Option INPUT_DIR = Option.builder("i")
            .longOpt("input-dir")
            .hasArg(true)
            .desc("input directory path")
            .required(false)
            .build();

    public static final String DEFAULT_INPUT_DIR = String.join(File.separator , Arrays.asList("..", "CodeExamples", "src", "main", "java"));

    private static Input parseInput(String[] args) {
        Options options = new Options();
        Collections.singletonList(INPUT_DIR).forEach(options::addOption);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine commandLine = parser.parse(options, args);
            return new Input(commandLine);
        } catch (ParseException e) {
            formatter.printHelp("help", options);
            throw new IllegalStateException("Could not parse Command Line", e);
        }
    }

    private static void printOutput(Map<String, Output> outputMap) {
        System.out.println("Result: ");
        List<OutputLine> outputLines =
                outputMap.entrySet().stream()
                        .map(e -> new OutputLine(e.getKey(), e.getValue().getLineNumber(), e.getValue().getLineNumberBonus(), e.getValue().getDependencies()))
                        .sorted(Comparator.comparing(OutputLine::getFileName))
                        .collect(Collectors.toList());
        outputLines.add(0, new OutputLine("File", "Source Lines", "Source Lines without Getters and Block Comments", "Dependencies"));
        int maxDirectoryName = outputLines.stream().map(OutputLine::getFileName).mapToInt(String::length).max().orElse(100);
        int maxLineNumber = outputLines.stream().map(OutputLine::getLineNumber).mapToInt(String::length).max().orElse(100);
        int maxLineNumberWithoutGetterAndSetter = outputLines.stream().map(OutputLine::getLineNumberWithoutGetterSetter).mapToInt(String::length).max().orElse(100);
        int maxDependencies = outputLines.stream().map(OutputLine::getDependencies).mapToInt(String::length).max().orElse(100);
        String lineFormat = "| %"+ maxDirectoryName+"s | %"+maxLineNumber+"s | %"+maxLineNumberWithoutGetterAndSetter+"s | %"+ maxDependencies+"s |%n";
        outputLines.forEach(line -> System.out.printf(lineFormat, line.getFileName(), line.getLineNumber(), line.getLineNumberWithoutGetterSetter(), line.getDependencies()));
    }

    public static void main(String[] args) {
        Input input = parseInput(args);
        Map<String, Output> outputMap = analyse(input);
        printOutput(outputMap);
    }
}
