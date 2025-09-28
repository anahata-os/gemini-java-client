/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini.functions.spi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import uno.anahata.gemini.functions.AITool;

/**
 * Provides functions for the model to compile and execute java code in the
 * current JVM.
 *
 * This functions can be stored and loaded as "gems" on the filesystem or can be
 * used to compile and run any java code.
 *
 * @author pablo
 */
public class RunningJVM {

    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(RunningJVM.class.getName());

    /**
     * A temporary map that the model can use to preserve state across calls.
     */
    public static Map chatTemp = Collections.synchronizedMap(new HashMap());

    /**
     * The default classpath that will be passed to the compiler as '-classpath'
     * option
     */
    public static String defaultCompilerClasspath = initDefaultCompilerClasspath();

    /**
     * The default parent classloader for compiled classes.
     */
    private static ClassLoader parentClassLoader = RunningJVM.class.getClassLoader();
/*
    private static String gemsDirPath;

    @AutomaticFunction("Sets the absolute path to the gems directory (the directory where the gems are stored)")
    public static void setGemsDirPath(@AutomaticFunction("The absolute path to the gems direcotry") String gemsDirPath) {
        RunningJVM.gemsDirPath = gemsDirPath;
    }

    @AutomaticFunction("Gets the absolute path to the gems directory (the directory where the gems are stored)")
    public static String getGemsDirPath() {
        return gemsDirPath;
    }
*/
    /**
     * Sets the default compiler class path to
     * System.getProperty("java.class.path") and returns this value
     *
     * @return System.getProperty("java.class.path")
     */
    public static String initDefaultCompilerClasspath() {
        defaultCompilerClasspath = System.getProperty("java.class.path");
        return System.getProperty("java.class.path");
    }

    @AITool("The classpath of the compiler used by RunningJVM. tools that require compilation")
    public static String getDefaultCompilerClasspath() {
        return defaultCompilerClasspath;
    }

    @AITool("Sets the classpath of the compiler used by RunningJVM. tools")
    public static void setDefaultCompilerClasspath(@AITool("The default classpath for all code compiled by RunningJVM tools") String defaultCompilerClasspath) {
        RunningJVM.defaultCompilerClasspath = defaultCompilerClasspath;
    }

    public static ClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    public static void setParentClassLoader(ClassLoader defaultParentClassLoader) {
        RunningJVM.parentClassLoader = defaultParentClassLoader;
    }

    /*
    @AutomaticFunction("Finds a Gem by its ID (filename) in the 'gemsDirPath' directory, compiles it, and executes it, returning the result.")
    public static Object runGem(@AutomaticFunction("The filename of the Gem to run, e.g., 'getWorkspaceOverview.java'") String gemId) throws Exception {
        logger.log(Level.INFO, "Executing Gem with ID: {0}", gemId);

        Path gemPath = Paths.get(gemsDirPath, gemId);

        if (!Files.exists(gemPath)) {
            throw new java.io.FileNotFoundException("Gem not found at: " + gemPath);
        }

        String sourceCode = Files.readString(gemPath);
        // Using null for extraClassPath and compilerOptions as Gems are expected to be self-contained.
        return compileAndExecuteJava(sourceCode, null, null);
    }

    @AutomaticFunction("Gets the file names of all available Gems in the gemsDirPath directory.")
    public static List<String> getGemIds() {

        Path gemsPath = Paths.get(gemsDirPath);

        if (gemsPath == null) {
            logger.log(Level.WARNING, "Gems dir path not found at: {0}", gemsPath);
            return Collections.emptyList();
        }

        if (!Files.isDirectory(gemsPath)) {
            logger.log(Level.WARNING, "Gems dir path is not a directory at: {0}", gemsPath);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(gemsPath)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading Gems directory", e);
            return Collections.singletonList("Error reading Gems directory: " + e.getMessage());
        }
    }
*/
    @AITool("Compiles the source code of a java class with whatever classpath is defined in RunningJVM.getDefaultCompilerClassPath")
    public static Class compileJava(
            @AITool("The source code")
            String sourceCode,
            @AITool("The class name")
            String className,
            @AITool("Additional classpath entries (if required)")
            String extraClassPath,
            @AITool("Additional compiler options (if required)")
            String[] compilerOptions) 
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            throw new RuntimeException("JDK required (running on JRE).");
        }

        String sourceFile = className + ".java";
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///" + sourceFile), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        ForwardingJavaFileManager<JavaFileManager> fileManager = new ForwardingJavaFileManager<JavaFileManager>(compiler.getStandardFileManager(diagnostics, null, null)) {
            private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<>();

            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                if (kind == JavaFileObject.Kind.CLASS) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    compiledClasses.put(className, outputStream);
                    return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + ".class"), JavaFileObject.Kind.CLASS) {
                        @Override
                        public OutputStream openOutputStream() throws IOException {
                            return outputStream;
                        }
                    };
                }
                return super.getJavaFileForOutput(location, className, kind, sibling);
            }

            public Map<String, byte[]> getCompiledClasses() {
                Map<String, byte[]> result = new HashMap<>();
                for (Map.Entry<String, ByteArrayOutputStream> entry : compiledClasses.entrySet()) {
                    result.put(entry.getKey(), entry.getValue().toByteArray());
                }
                return result;
            }
        };

        String classpath = defaultCompilerClasspath;
        if (extraClassPath != null && !extraClassPath.isEmpty()) {
            classpath += File.pathSeparator + extraClassPath;
        }

        List<String> options = new ArrayList<>(Arrays.asList("-classpath", classpath));

        if (compilerOptions != null) {
            options.addAll(Arrays.asList(compilerOptions));
        }
        if (!options.contains("-proc:none")) {
            options.add("-proc:none");
        }
        logger.log(Level.INFO, "Compiling with options: \n{0}", options);

        StringWriter writer = new StringWriter();
        JavaCompiler.CompilationTask task = compiler.getTask(writer, fileManager, diagnostics, options, null, Collections.singletonList(source));
        boolean success = task.call();
        logger.log(Level.INFO, "Compilation Success: {0}", success);

        if (!success) {
            StringBuilder error = new StringBuilder("Compiler: " + compiler + "\n");
            error.append("Options:" + options + "\n");
            error.append("Task:" + task + "\n");
            error.append("Diagnostics:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                error.append(diagnostic.toString()).append("\n");
                logger.log(Level.INFO, "Compiler Diagnostic: {0}", diagnostic.toString());
            }
            System.out.println(error);
            throw new java.lang.RuntimeException("Compilation error:\n" + error.toString());
        }

        Map<String, byte[]> compiledClasses = ((Map<String, byte[]>) fileManager.getClass().getMethod("getCompiledClasses").invoke(fileManager));
        ClassLoader classLoader = new ClassLoader(parentClassLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = compiledClasses.get(name);
                if (bytes != null) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        };

        Class dynamicClass = (Class) classLoader.loadClass(className);
        return dynamicClass;
    }

    @AITool(
            "Compiles and executes Java source code in the users JVM.\n"
            + "The compiler will use the classpath set in the public static field 'uno.anahata.gemini.functions.spi.RunningJVM.defaultCompilerClasspath' plus any extra classpath explicitely passed as an argument to the function (if any).\n"
            + "The call() method will be executed and a FunctionResponse will be sent back to the model inmediatly with either 'output' (if success) or 'error' (if an exception occurs during compilation or execution).\n"
            + "If the returend value is 'null', an empty string will be sent as the 'result' field of FunctionResponse.\n"
            + "The model can also use a temporary public static Map located at: 'uno.anahata.gemini.functions.spi.RunningJVM.chatTemp' to persist state across multiple calls."
    )
    public static Object compileAndExecuteJava(
            @AITool("Source code of a java class called 'Gemini' that implements java.util.concurrent.Callable") String sourceCode,
            @AITool("Compiler's additional classpath entries separated with File.pathSeparator. ") String extraClassPath,
            @AITool("Compiler's additional options.") String[] compilerOptions) throws Exception {

        
        logger.log(Level.INFO, "executeJavaCode: \nsource={0}", sourceCode);
        logger.log(Level.INFO, "executeJavaCode: \nextraCompilerClassPath={0}", extraClassPath);

        Class c = compileJava(sourceCode, "Gemini", extraClassPath, compilerOptions);
        Object o = c.newInstance();

        if (o instanceof Callable) {
            logger.info("Calling call() method");
            Callable trueGeminiInstance = (Callable) o;
            Object ret = ensureJsonSerializable(trueGeminiInstance.call());            
            logger.log(Level.INFO, "call() method returned {0}", ret);
            return ret;
        } else {
            throw new RuntimeException("Source file should implement java.util.Callable (thoooo)");
        }

    }

    @SuppressWarnings("unchecked")
    private static Object ensureJsonSerializable(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        } else if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                ensureJsonSerializable(e.getKey());
                ensureJsonSerializable(e.getValue());
            }
        } else if (value instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) value;
            for (Object o : iterable) {
                ensureJsonSerializable(o);
            }
        }

        return value.toString();
    }
}
