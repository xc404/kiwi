package com.kiwi.common.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 子进程标准输出/错误流排空与 {@link Process#waitFor(long, TimeUnit)} 的工具类。
 * <p>
 * 若在进程尚未退出时先调用 {@link Process#waitFor()} 且不在其他线程读取管道，子进程大量写 stdout/stderr 会塞满管道缓冲区，
 * 导致子进程阻塞、父进程永远等不到退出，表现为「死锁」或超时无效。本类在后台线程并行排空各流，再等待进程结束。
 */
public final class ProcessHelper {

    private ProcessHelper() {
    }

    /**
     * @param exitCode  子进程退出码
     * @param stdout    标准输出字节（子进程 stdout）
     * @param stderr    标准错误字节；若创建进程时使用了 {@link ProcessBuilder#redirectErrorStream(boolean) redirectErrorStream(true)}，
     *                  则为空数组（此时 stderr 已合并进 stdout）
     */
    public record StreamResult(int exitCode, byte[] stdout, byte[] stderr) {
    }

    /**
     * 在后台线程排空子进程输出流，再等待其结束。
     *
     * @param process            已 {@link ProcessBuilder#start() 启动} 的进程
     * @param mergedErrorStream  是否与创建该进程时的 {@link ProcessBuilder#redirectErrorStream(boolean)} 一致；
     *                           为 true 时仅排空 {@link Process#getInputStream()}（合并后的流）
     * @param timeout            等待进程结束的最长时间；≤0 表示无限等待（仍先排空管道，避免死锁）
     * @param unit               与 {@code timeout} 配套的时间单位；{@code timeout≤0} 时忽略
     * @throws TimeoutException  在限时内进程未结束（已 {@link Process#destroyForcibly()}）
     */
    public static StreamResult waitForDrain(
            Process process,
            boolean mergedErrorStream,
            long timeout,
            TimeUnit unit
    ) throws IOException, InterruptedException, TimeoutException {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        AtomicReference<IOException> readErr = new AtomicReference<>();

        Thread outThread = startDrain(process.getInputStream(), outBuf, "process-helper-stdout", readErr);

        Thread errThread = null;
        if (!mergedErrorStream) {
            InputStream err = process.getErrorStream();
            if (err != null) {
                errThread = startDrain(err, errBuf, "process-helper-stderr", readErr);
            }
        }

        boolean finished;
        if (timeout <= 0) {
            process.waitFor();
            finished = true;
        } else {
            finished = process.waitFor(timeout, unit);
        }

        if (!finished) {
            process.destroyForcibly();
            reapQuiet(process, 5, TimeUnit.SECONDS);
            joinQuiet(outThread, 5_000);
            joinQuiet(errThread, 5_000);
            throw new TimeoutException("process did not finish within " + timeout + " " + unit);
        }

        long joinMs = timeout <= 0
                ? TimeUnit.MINUTES.toMillis(2)
                : Math.min(TimeUnit.MINUTES.toMillis(5), unit.toMillis(timeout) + TimeUnit.SECONDS.toMillis(30));
        joinQuiet(outThread, joinMs);
        joinQuiet(errThread, joinMs);

        IOException io = readErr.get();
        if (io != null) {
            throw new IOException("failed to read subprocess streams: " + io.getMessage(), io);
        }

        int exit = process.exitValue();
        byte[] out = outBuf.toByteArray();
        byte[] err = mergedErrorStream ? new byte[0] : errBuf.toByteArray();
        return new StreamResult(exit, out, err);
    }

    private static Thread startDrain(
            InputStream in,
            ByteArrayOutputStream target,
            String threadName,
            AtomicReference<IOException> readErr
    ) {
        if (in == null) {
            return null;
        }
        Thread t = new Thread(() -> {
            try (InputStream stream = in) {
                stream.transferTo(target);
            } catch (IOException e) {
                readErr.set(e);
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuiet(Thread t, long maxWaitMs) throws InterruptedException {
        if (t != null && t.isAlive()) {
            t.join(maxWaitMs);
        }
    }

    private static void reapQuiet(Process process, long timeout, TimeUnit unit) {
        try {
            process.waitFor(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
