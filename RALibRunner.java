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

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java RALibRunner <java_or_class_file> <config_path> <target_method>");
            return;
        }

        javaFile = args[0];
        configPath = args[1];
        targetMethod = args[2];

        try {
            if (!compileJavaIfNeeded()) return;
            if (!checkTargetMethod()) return;
            if (!runRALib()) return;
            cleanModelXml();
            listMethodsInModelXml(); // <-- List methods so you know what to use
            if (!runCoGent()) return;
            System.out.println("\n[+] RALib and CoGent completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
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
            for (String m : methods) {
                System.out.println("    • " + m);
            }
            br.close();
            System.out.println("[+] Use exactly one of the above as <target_method> for your CoGent step.\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean runCoGent() {
        try {
            System.out.println("\n=== Running CoGent ===\n");
            ProcessBuilder cogentBuilder = new ProcessBuilder(
                    "python3",
                    "CoGent/main.py",
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
            System.out.println("[+] CoGent completed, refinement complete.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
