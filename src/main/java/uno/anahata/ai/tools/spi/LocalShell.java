package uno.anahata.ai.tools.spi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import uno.anahata.ai.tools.AIToolMethod;
import uno.anahata.ai.tools.AIToolParam;

public class LocalShell {

    @AIToolMethod("Runs a shell command with bash -c: <command> and returns a map with the following values:\n"
            + " \n\tthreadId (it of the thread that executed the shell command, "
            + " \n\tpid, process id"
            + " \n\tstartTime, process start time as an ISO-8601 string"
            + " \n\tendTime, process end time as an ISO-8601 string"
            + " \n\texecutionTimeMs, process execution time in ms."
            + " \n\texitCode, process exit code"
            + " \n\tstdout, process standard output"
            + " \n\tstderr, process standard error"
    )
    public static Map<String, Object> runShell(@AIToolParam("The command to run") String command) throws Exception {
        System.out.println("executeShellCommand: " + command);
        Map<String, Object> result = new HashMap<>();

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(false);

        Instant startTime = Instant.now();
        Process process = pb.start();

        String pid = "unknown";
        try {
            pid = "" + process.pid();
        } catch (UnsupportedOperationException e) {
            // PID not available on some JVMs
        }

        ExecutorService executor = uno.anahata.ai.Executors.cachedThreadPool;
        Future<String> stdoutFuture = executor.submit(new StreamGobbler(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(new StreamGobbler(process.getErrorStream()));

        int exitCode = process.waitFor();
        Instant endTime = Instant.now();

        String output = stdoutFuture.get();
        String error = stderrFuture.get();
        long executionTimeMs = endTime.toEpochMilli() - startTime.toEpochMilli();

        result.put("threadId", Thread.currentThread().getName());
        result.put("pid", pid);
        result.put("startTime", startTime.toString());
        result.put("endTime", endTime.toString());
        result.put("executionTimeMs", executionTimeMs);
        result.put("exitCode", exitCode);
        result.put("stdout", output);
        result.put("stderr", error);

        return result;
    }

    private static class StreamGobbler implements Callable<String> {
        private final InputStream is;

        StreamGobbler(java.io.InputStream inputStream) {
            this.is = inputStream;
        }

        @Override
        public String call() throws IOException {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = is) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
            }
            return sb.toString();
        }
    }
}
