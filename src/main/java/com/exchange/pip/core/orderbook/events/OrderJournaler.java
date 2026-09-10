package com.exchange.pip.core.orderbook.events;

import com.exchange.pip.core.orderbook.command.OrderCommandCodec;
import com.exchange.pip.core.symbol.SymbolUtils;
import com.lmax.disruptor.EventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ApplicationScoped
public final class OrderJournaler implements EventHandler<OrderEvent>, AutoCloseable {

    private static final int BUFFER_CAPACITY = 1024 * 1024; // 1MB buffer
    private static final Pattern WAL_PATTERN = Pattern.compile("wal-(\\d+)\\.log");
    private static final String WAL_NAME_FMT = "wal-%06d.log";

    private final Path walDir;
    private final int rotateEveryN;
    private final ByteBuffer buffer;

    private FileChannel channel;
    private long currentVersion;
    private int recordsSinceRotation;
    private long lastSequenceWritten = -1;

    public OrderJournaler(
            @ConfigProperty(name = "walDir", defaultValue = "/tmp/pip-engine/wal") String walDir,
            @ConfigProperty(name = "walRotateEveryN", defaultValue = "1000000") int rotateEveryN
    ) throws IOException {
        this.walDir = Paths.get(walDir);
        this.rotateEveryN = rotateEveryN;
        this.buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        try {
            Files.createDirectories(this.walDir);
        } catch (FileAlreadyExistsException ignored) {
        }

        this.currentVersion = findLatestVersion(WAL_PATTERN, "wal").orElse(0L);
        openChannelForCurrentVersion();
    }

    private void openChannelForCurrentVersion() throws IOException {
        Path walPath = walDir.resolve(String.format(WAL_NAME_FMT, currentVersion));
        @SuppressWarnings("resource")
        RandomAccessFile raf = new RandomAccessFile(walPath.toFile(), "rw");
        this.channel = raf.getChannel();
        this.channel.position(this.channel.size());
        this.recordsSinceRotation = (int) (channel.size() / OrderCommandCodec.RECORD_SIZE);
    }

    @Override
    public void onEvent(OrderEvent event, long ringBufferSequence, boolean endOfBatch) throws Exception {
        if (event.getOrder() == null) {
            return;
        }

        if (buffer.remaining() < OrderCommandCodec.RECORD_SIZE) {
            flushToDisk();
        }

        // Use the durable sequence stamped by OrderSequencer upstream — NOT the
        // Disruptor's own ring-buffer parameter, which resets to 0 on every restart
        // and must never be persisted (see OrderSequencer for where this is assigned).
        long durableSequence = event.getSequence();

        long packedSymbol = SymbolUtils.encodeSymbol(event.getOrder().symbol());
        OrderCommandCodec.encode(buffer, durableSequence, event.getOrder(), packedSymbol);
        lastSequenceWritten = durableSequence;
        recordsSinceRotation++;

        if (endOfBatch) {
            flushToDisk();
            // Only rotate on a batch boundary — never split a batch across two WAL files
            if(recordsSinceRotation >= rotateEveryN)
                rotate();
        }
    }

    /**
     * Rotates to a new WAL version. Snapshot-taking is NOT done here — see note below.
     * This just marks the boundary; the snapshot handler (a separate consumer/listener)
     * should snapshot at the same sequence and tag its file with the same version.
     */
    private void rotate() throws IOException {
        channel.force(true);
        channel.close();

        long newVersion = currentVersion + 1;
        currentVersion = newVersion;
        recordsSinceRotation = 0;
        openChannelForCurrentVersion();

        // Fire an event/callback here so the snapshot component knows to snapshot
        // at lastSequenceWritten and tag its file as snapshot-<newVersion>.dat
        onRotate(newVersion, lastSequenceWritten);
    }

    /** Hook for whatever mechanism notifies the snapshot component. Wire this to your bus/event. */
    private void onRotate(long newVersion, long sequenceAtRotation) {
    }

    private Optional<Long> findLatestVersion(Pattern pattern, String prefix) throws IOException {
        if (!Files.exists(walDir)) return Optional.empty();
        try (Stream<Path> files = Files.list(walDir)) {
            return files
                    .map(p -> pattern.matcher(p.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(m -> Long.parseLong(m.group(1)))
                    .max(Long::compareTo);
        }
    }

    public void flushToDisk() throws IOException {
        if (buffer.position() > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            flushToDisk();
            channel.force(true);
        } finally {
            channel.close();
        }
    }
}