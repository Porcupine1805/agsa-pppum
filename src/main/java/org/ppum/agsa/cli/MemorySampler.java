package org.ppum.agsa.cli;

/** Lightweight peak-heap sampler for experiment reporting. */
final class MemorySampler implements AutoCloseable {
    private volatile boolean running = true;
    private volatile long peakBytes = used();
    private final Thread thread;

    MemorySampler() {
        thread = new Thread(() -> {
            while (running) {
                peakBytes = Math.max(peakBytes, used());
                try { Thread.sleep(20L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
            peakBytes = Math.max(peakBytes, used());
        }, "agsa-memory-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    double peakMiB() { return peakBytes / 1048576.0; }

    @Override public void close() {
        running = false;
        try { thread.join(100L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        peakBytes = Math.max(peakBytes, used());
    }

    private static long used() {
        Runtime r=Runtime.getRuntime();
        return r.totalMemory()-r.freeMemory();
    }
}
