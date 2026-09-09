package com.autoparts.inventory.util;

import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 4180 CSV builder. Fields containing a comma, quote, CR or LF are
 * quoted and inner quotes doubled. Rows end with CRLF.
 */
public final class CsvWriter {
    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
        return this;
    }

    /** Emit an empty line, e.g. to separate sections. */
    public CsvWriter blankLine() {
        sb.append("\r\n");
        return this;
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public byte[] toBytes() {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
