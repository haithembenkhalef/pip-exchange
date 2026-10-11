package com.exchange.pip.core.orderbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class OrderBookSnapshot {

    static final Path snapshotDir = Path.of("/tmp/snapshots");

    public OrderBookSnapshot() {
        try {
            Files.createDirectories(snapshotDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void snapshotBook(OrderBook orderBook, long lastAppliedSeq) throws IOException {
        String symbol = orderBook.getSymbol();
        Path file = pathFor(snapshotDir, symbol, lastAppliedSeq);
        OrderBookSnapshotCodec.write(orderBook, file);
    }

    public OrderBook restoreBookFromSnapshot(String symbol) throws IOException {
        if (!Files.isDirectory(snapshotDir)) return null;

        String prefix = safeSymbol(symbol) + "-";
        List<Path> candidates;
        try (var s = Files.list(snapshotDir)) {
            candidates = s.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(prefix) && n.endsWith(".snap");   // ignores .tmp
                    })
                    .sorted(Comparator.reverseOrder())                         // newest first
                    .toList();
        }
        for (Path p : candidates) {                      // empty list -> loop skipped -> null
            try {
                return OrderBookSnapshotCodec.load(p);
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();                     // corrupt: fall back to the next older one
            }
        }
        return null;
    }

    private static String safeSymbol(String symbol) {
        return symbol.replaceAll("[^A-Za-z0-9_-]", "_");      // "BTC/USD" -> "BTC_USD"
    }

    static Path pathFor(Path dir, String symbol, long lastAppliedSeq) {
        return dir.resolve(String.format("%s-%020d.snap", safeSymbol(symbol), lastAppliedSeq));
    }


}
