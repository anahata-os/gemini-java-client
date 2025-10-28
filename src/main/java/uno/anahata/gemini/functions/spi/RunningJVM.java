package uno.anahata.gemini.functions.spi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.gemini.functions.AIToolMethod;
import uno.anahata.gemini.functions.AIToolParam;

/**
 * Provides functions for the model to compile and execute java code in the
 * current JVM.
 *
 * @author pablo
 */
@Slf4j
public class RunningJVM {

    public static Map chatTemp = Collections.synchronizedMap(new HashMap());
    public static String defaultCompilerClasspath = initDefaultCompilerClasspath();
    private static ClassLoader parentClassLoader = RunningJVM.class.getClassLoader();

    public static String initDefaultCompilerClasspath() {
        defaultCompilerClasspath = System.getProperty("java.class.path");
        return System.getProperty("java.class.path");
    }

    @AIToolMethod("The classpath of the compiler used by RunningJVM. tools that require compilation")
    public static String getDefaultCompilerClasspath() {
        return defaultCompilerClasspath;
    }

    @AIToolMethod("Sets the classpath of the compiler used by RunningJVM. tools")
    public static void setDefaultCompilerClasspath(@AIToolParam("The default classpath for all code compiled by RunningJVM tools") String defaultCompilerClasspath) {
        RunningJVM.defaultCompilerClasspath = defaultCompilerClasspath;
    }

    public static ClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    public static void setParentClassLoader(ClassLoader defaultParentClassLoader) {
        RunningJVM.parentClassLoader = defaultParentClassLoader;
    }

    @AIToolMethod("Compiles the source code of a java class with whatever classpath is defined in RunningJVM.getDefaultCompilerClassPath")
    public static Class compileJava(
            @AIToolParam("The source code") String sourceCode,
            @AIToolParam("The class name") String className,
            @AIToolParam("Additional classpath entries (if required)") String extraClassPath,
            @AIToolParam("Additional compiler options (if required)") String[] compilerOptions) 
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
        log.info("Compiling with options: \n{}", options);

        StringWriter writer = new StringWriter();
        JavaCompiler.CompilationTask task = compiler.getTask(writer, fileManager, diagnostics, options, null, Collections.singletonList(source));
        boolean success = task.call();
        log.info("Compilation Success: {}", success);

        if (!success) {
            StringBuilder error = new StringBuilder("Compiler: " + compiler + "\n");
            error.append("Task:" + task + "\n");
            error.append("Diagnostics:\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                error.append(diagnostic.toString()).append("\n");
                log.info("Compiler Diagnostic: {}", diagnostic.toString());
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

    @AIToolMethod(
            value = "Compiles and executes Java source code in the users JVM.\n"
            + "The compiler will use the classpath set in the public static field 'uno.anahata.gemini.functions.spi.RunningJVM.defaultCompilerClasspath' plus any extra classpath explicitely passed as an argument to the function (if any).\n"
            + "The call() method will be executed and a FunctionResponse will be sent back to the model inmediatly with either 'output' (if success) or 'error' (if an exception occurs during compilation or execution).\n"
            + "If the returend value is 'null', an empty string will be sent as the 'result' field of FunctionResponse.\n"
            + "The model can also use a temporary public static Map located at: 'uno.anahata.gemini.functions.spi.RunningJVM.chatTemp' to persist state across multiple calls.",
            requiresApproval = true
    )
    public static Object compileAndExecuteJava(
            @AIToolParam("Source code of a java class called 'Gemini' that implements java.util.concurrent.Callable") String sourceCode,
            @AIToolParam("Compiler's additional classpath entries separated with File.pathSeparator. ") String extraClassPath,
            @AIToolParam("Compiler's additional options.") String[] compilerOptions) throws Exception {

        
        log.info("executeJavaCode: \nsource={}", sourceCode);
        log.info("executeJavaCode: \nextraCompilerClassPath={}", extraClassPath);

        Class c = compileJava(sourceCode, "Gemini", extraClassPath, compilerOptions);
        Object o = c.newInstance();

        if (o instanceof Callable) {
            log.info("Calling call() method");
            Callable trueGeminiInstance = (Callable) o;
            Object ret = ensureJsonSerializable(trueGeminiInstance.call());            
            log.info("call() method returned {}", ret);
            return ret;
        } else {
            throw new RuntimeException("Source file should implement java.util.Callable");
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
