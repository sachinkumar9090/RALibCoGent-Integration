import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Collectors;
import java.util.regex.*;
import java.util.*;

class RALibRunner {

    private static String javaFile;
    private static String configPath;
    private static String targetMethod;
    private static String existingModelXml;
    private static boolean useExistingModel = false;

    public static void main(String[] args) {
        if (!parseArguments(args)) {
            printUsage();
            return;
        }

        try {
            if (useExistingModel) {
                System.out.println("[+] Using existing model.xml: " + existingModelXml);
                if (!validateExistingModel()) return;
                if (!copyModelXml()) return;
            } else {
                System.out.println("[+] Generating new model using RALib...");
                if (!compileJavaIfNeeded()) return;
                if (!checkTargetMethod()) return;
                if (!runRALib()) return;
                cleanModelXml();
            }

            listMethodsInModelXml();
            if (!runCoGent()) return;
            System.out.println("\n[+] Contract generation completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean parseArguments(String[] args) {
        if (args.length < 3) {
            return false;
        }

        // Check for -m switch (existing model)
        if (args.length >= 3 && args[0].equals("-m")) {
            useExistingModel = true;
            existingModelXml = args[1];
            targetMethod = args[2];

            // Optional: If more arguments provided, treat as additional parameters
            if (args.length > 3) {
                System.out.println("[+] Additional parameters ignored: " + Arrays.toString(Arrays.copyOfRange(args, 3, args.length)));
            }
            return true;
        }

        // Standard mode: java_file config_path target_method
        if (args.length == 3) {
            javaFile = args[0];
            configPath = args[1];
            targetMethod = args[2];
            return true;
        }

        return false;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Standard mode (generate new model):");
        System.out.println("    java -jar ralib-fat-runner.jar <java_or_class_file> <config_path> <target_method>");
        System.out.println("");
        System.out.println("  Existing model mode (use existing model.xml):");
        System.out.println("    java -jar ralib-fat-runner.jar -m <existing_model.xml> <target_method>");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  java -jar ralib-fat-runner.jar Stack.java config.yaml push");
        System.out.println("  java -jar ralib-fat-runner.jar -m existing_model.xml push");
    }

    private static boolean validateExistingModel() {
        File modelFile = new File(existingModelXml);
        if (!modelFile.exists()) {
            System.out.println("[-] Existing model file not found: " + existingModelXml);
            return false;
        }

        if (!modelFile.canRead()) {
            System.out.println("[-] Cannot read existing model file: " + existingModelXml);
            return false;
        }

        // Basic validation - check if it's a valid XML file
        try {
            BufferedReader reader = new BufferedReader(new FileReader(modelFile));
            String firstLine = reader.readLine();
            reader.close();

            if (firstLine == null || !firstLine.trim().startsWith("<?xml")) {
                System.out.println("[-] Invalid XML file: " + existingModelXml);
                return false;
            }
        } catch (IOException e) {
            System.out.println("[-] Error reading existing model file: " + e.getMessage());
            return false;
        }

        System.out.println("[+] Existing model file validated: " + existingModelXml);
        return true;
    }

    private static boolean copyModelXml() {
        try {
            File sourceFile = new File(existingModelXml);
            File targetFile = new File("model.xml");

            // If source and target are the same, no need to copy
            if (sourceFile.getCanonicalPath().equals(targetFile.getCanonicalPath())) {
                System.out.println("[+] Model file already in correct location.");
                return true;
            }

            System.out.println("[+] Copying model file to working directory...");

            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }

            System.out.println("[+] Model file copied successfully.");
            return true;
        } catch (IOException e) {
            System.out.println("[-] Error copying model file: " + e.getMessage());
            return false;
        }
    }

    private static boolean compileJavaIfNeeded() throws IOException, InterruptedException {
        File file = new File(javaFile);
        if (!file.exists()) {
            System.out.println("[-] File not found: " + javaFile);
            return false;
        }

        if (javaFile.endsWith(".java")) {
            System.out.println("[+] Compiling " + javaFile);
            Process compile = new ProcessBuilder("javac", javaFile).inheritIO().start();
            int result = compile.waitFor();
            if (result != 0) {
                System.out.println("[-] Compilation failed for " + javaFile);
                return false;
            }
        }
        return true;
    }

    private static boolean checkTargetMethod() {
        try {
            String className = new File(javaFile.replace(".java", "").replace(".class", "")).getName();
            File classFile = new File(className + ".class");
            if (!classFile.exists()) {
                System.out.println("[-] Compiled class file not found: " + classFile.getAbsolutePath());
                return false;
            }

            File parentFile = classFile.getParentFile();
            URLClassLoader classLoader;
            if (parentFile != null) {
                classLoader = URLClassLoader.newInstance(new URL[]{parentFile.toURI().toURL()});
            } else {
                classLoader = URLClassLoader.newInstance(new URL[]{new File(".").toURI().toURL()});
            }

            Class<?> loadedClass = Class.forName(className, true, classLoader);
            for (Method m : loadedClass.getDeclaredMethods()) {
                if (m.getName().equals(targetMethod)) {
                    System.out.println("[+] Target method found: " + targetMethod);
                    return true;
                }
            }
            System.out.println("[-] Target method not found in Java class: " + targetMethod);
            System.out.println("[-] Proceeding anyway, ensure you pass the exact name as in model.xml.");
            return true; // Allow to proceed to let user see available methods
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean runRALib() {
        try {
            System.out.println("\n=== Running RALib ===\n");
            ProcessBuilder ralibBuilder = new ProcessBuilder(
                    "java", "-ea", "-cp",
                    "ralib/target/ralib-0.1-SNAPSHOT-jar-with-dependencies.jar:.",  // add '.' for current dir
                    "de.learnlib.ralib.Main",
                    "class-analyzer",
                    "-f",
                    configPath
            );
            ralibBuilder.inheritIO();
            Process ralibProcess = ralibBuilder.start();
            int result = ralibProcess.waitFor();
            if (result != 0) {
                System.out.println("[-] RALib process failed.");
                return false;
            }
            System.out.println("[+] RALib completed, model.xml generated.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void cleanModelXml() {
        try {
            File xmlFile = new File("model.xml");
            if (xmlFile.exists()) {
                System.out.println("[+] Cleaning model.xml...");
                String content = new BufferedReader(new FileReader(xmlFile))
                        .lines()
                        .collect(Collectors.joining("\n"));

                // Removes single quotes around identifiers like 'r1', 'r2', 'r3', 'p1'
                content = content.replaceAll("'([rp]\\d+)'", "$1");

                try (FileWriter writer = new FileWriter(xmlFile)) {
                    writer.write(content);
                }
                System.out.println("[+] model.xml cleaned successfully.\n");
            } else {
                System.err.println("[-] model.xml not found. Cannot clean.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void listMethodsInModelXml() {
        try {
            File xmlFile = new File("model.xml");
            if (!xmlFile.exists()) {
                System.out.println("[-] model.xml not found, cannot list methods.");
                return;
            }
            System.out.println("[+] Available methods in model.xml:");
            BufferedReader br = new BufferedReader(new FileReader(xmlFile));
            String line;
            Pattern pattern = Pattern.compile("<symbol name=\"([^\"]+)\"");
            Set<String> methods = new LinkedHashSet<>();
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    methods.add(matcher.group(1));
                }
            }

            if (methods.isEmpty()) {
                System.out.println("    No methods found in model.xml");
            } else {
                for (String m : methods) {
                    System.out.println("    • " + m);
                }
            }
            br.close();

            // Validate that target method exists in the model
            if (!methods.contains(targetMethod)) {
                System.out.println("[-] WARNING: Target method '" + targetMethod + "' not found in model.xml");
                System.out.println("[-] Available methods are listed above. Please use one of them.");
            } else {
                System.out.println("[+] Target method '" + targetMethod + "' found in model.xml");
            }
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean runCoGent() {
        try {
            System.out.println("\n=== Running CoGent ===\n");
            ProcessBuilder cogentBuilder = new ProcessBuilder(
                    "python3",
                    "cogent/main.py",  // Updated path to match CMakeLists.txt
                    "-i",
                    "model.xml",
                    "-t",
                    targetMethod
            );
            cogentBuilder.inheritIO();
            Process cogentProcess = cogentBuilder.start();
            int result = cogentProcess.waitFor();
            if (result != 0) {
                System.out.println("[-] CoGent process failed.");
                return false;
            }
            System.out.println("[+] CoGent completed, contract generation successful.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}