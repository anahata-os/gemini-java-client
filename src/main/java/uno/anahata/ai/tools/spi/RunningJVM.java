/* Licensed under the Apache License, Version 2.0 */
package uno.anahata.ai.tools.spi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

/**
 * Provides functions for the model to compile and execute java code in the
 * current JVM.
 *
 * @author anahata
 */
@Slf4j
public class RunningJVM {

    public static Map chatTemp = Collections.synchronizedMap(new HashMap());
    public static String defaultCompilerClasspath = initDefaultCompilerClasspath();

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
    
    @AIToolMethod(value = "Gets a formatted string of the default compiler classpath, grouped by directory.", requiresApproval = false)
    public static String getPrettyPrintedDefaultCompilerClasspath() {
        return ClasspathPrinter.prettyPrint(defaultCompilerClasspath);
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
        
        if (extraClassPath != null) {
            log.info("extraClassPath: {} entries:\n{}", extraClassPath.split(File.pathSeparator).length, extraClassPath);
        }
        
        log.info("defaultCompilerClasspath: {} entries:", defaultCompilerClasspath.split(File.pathSeparator).length);

        String classpath = defaultCompilerClasspath;
        if (extraClassPath != null && !extraClassPath.isEmpty()) {
            // CRITICAL FIX: Prepend extraClassPath to ensure hot-reloaded classes take precedence
            classpath = extraClassPath + File.pathSeparator + classpath;
        }
        
        log.info("total classpathEntries: {} entries:", classpath.split(File.pathSeparator).length);
        if (compilerOptions != null) {
            log.info("compilerOptions:", Arrays.asList(compilerOptions));
        }
        

        List<String> options = new ArrayList<>(Arrays.asList("-classpath", classpath));

        if (compilerOptions != null) {
            options.addAll(Arrays.asList(compilerOptions));
        }
        
        // START of new code
        boolean hasVersionFlag = false;
        if (compilerOptions != null) {
            for (String option : compilerOptions) {
                if (option.equals("--release") || option.equals("-source") || option.equals("-target")) {
                    hasVersionFlag = true;
                    break;
                }
            }
        }

        if (!hasVersionFlag) {
            String runtimeVersion = System.getProperty("java.specification.version");
            log.info("No explicit Java version compiler flag found. Defaulting to --release {}.", runtimeVersion);
            options.add("--release");
            options.add(runtimeVersion);
        }
        // END of new code
        
        if (!options.contains("-proc:none")) {
            options.add("-proc:none");
        }
        log.debug("Compiling with options: \n{}", options);

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

        List<URL> urlList = new ArrayList<>();
        if (extraClassPath != null && !extraClassPath.isEmpty()) {
            String[] pathElements = extraClassPath.split(File.pathSeparator);
            for (String element : pathElements) {
                try {
                    urlList.add(new File(element).toURI().toURL());
                } catch (Exception e) {
                    log.warn("Invalid classpath entry: {}", element, e);
                }
            }
        }

        URLClassLoader reloadingClassLoader = new URLClassLoader(urlList.toArray(new URL[0]), Thread.currentThread().getContextClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    // 1. Check if class is already loaded by this loader
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        // 2. Check for our in-memory compiled class first (the "hot-reload" part for Anahata.java)
                        byte[] bytes = compiledClasses.get(name);
                        if (bytes != null) {
                            log.info("Hot-reloading in-memory class: {}", name);
                            c = defineClass(name, bytes, 0, bytes.length);
                        } else {
                            try {
                                // 3. CHILD-FIRST: Try to find the class in our own URLs (e.g., target/classes)
                                c = findClass(name);
                                log.info("Loaded class from extraClassPath (Child-First): {}", name);
                            } catch (ClassNotFoundException e) {
                                // 4. PARENT-LAST: If not found, delegate to the parent classloader.
                                c = super.loadClass(name, resolve);
                            }
                        }
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
        };

        return reloadingClassLoader.loadClass(className);
    }

    @AIToolMethod(
            value = "Compiles and executes Java source code in the users JVM.\n"
            + "The compiler will use the classpath set in the public static field 'uno.anahata.ai.tools.spi.RunningJVM.defaultCompilerClasspath' plus any extra classpath explicitely passed as an argument to the function (if any).\n"
            + "The call() method will be executed and a FunctionResponse will be sent back to the model inmediatly with either 'output' (if success) or 'error' (if an exception occurs during compilation or execution).\n"
            + "If the returend value is 'null', an empty string will be sent as the 'result' field of FunctionResponse.\n"
            + "The model can also use a temporary public static Map located at: 'uno.anahata.ai.tools.spi.RunningJVM.chatTemp' to persist state across multiple calls.",
            requiresApproval = true
    )
    public static Object compileAndExecuteJava(
            @AIToolParam("Source code of a java class called 'Anahata' that:\n  "
                    + "a) is **public**\n"
                    + "b) has **no package declaration**\n"
                    + "c) implements **java.util.concurrent.Callable**") String sourceCode,
            @AIToolParam("Compiler's additional classpath entries separated with File.pathSeparator. ") String extraClassPath,
            @AIToolParam("Compiler's additional options.") String[] compilerOptions) throws Exception {

        log.info("executeJavaCode: \nsource={}", sourceCode);
        log.info("executeJavaCode: \nextraCompilerClassPath={}", extraClassPath);

        Class c = compileJava(sourceCode, "Anahata", extraClassPath, compilerOptions);
        Object o = c.getDeclaredConstructor().newInstance();

        if (o instanceof Callable) {
            log.info("Calling call() method");
            Callable trueAnahataInstance = (Callable) o;
            Object ret = ensureJsonSerializable(trueAnahataInstance.call());
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