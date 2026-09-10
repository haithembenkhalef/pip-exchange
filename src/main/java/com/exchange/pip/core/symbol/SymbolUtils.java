package com.exchange.pip.core.symbol;

public final class SymbolUtils {

    /**
     * Packs an ASCII symbol string (up to 8 chars) into a single 64-bit long.
     * Zero-allocation if called on symbol bytes/chars.
     */
    public static long encodeSymbol(String symbol) {
        long val = 0;
        int len = Math.min(symbol.length(), 8);
        for (int i = 0; i < len; i++) {
            val |= ((long) (symbol.charAt(i) & 0xFF)) << (i * 8);
        }
        return val;
    }

    /**
     * Unpacks a 64-bit long back into a String during WAL recovery.
     */
    public static String decodeSymbol(long packed) {
        char[] chars = new char[8];
        int len = 0;
        for (int i = 0; i < 8; i++) {
            byte b = (byte) ((packed >> (i * 8)) & 0xFF);
            if (b == 0) break;
            chars[len++] = (char) b;
        }
        return new String(chars, 0, len);
    }
}