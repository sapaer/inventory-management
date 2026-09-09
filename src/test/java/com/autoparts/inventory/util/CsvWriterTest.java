package com.autoparts.inventory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {
    @Test
    void plainRowIsCommaJoinedWithCrlf() {
        String out = new CsvWriter().row("a", "b", 3).toString();
        assertEquals("a,b,3\r\n", out);
    }

    @Test
    void nullBecomesEmptyField() {
        assertEquals(",x\r\n", new CsvWriter().row(null, "x").toString());
    }

    @Test
    void quotesCommasNewlinesAndDoublesInnerQuotes() {
        String out = new CsvWriter().row("a,b", "he said \"hi\"", "line1\nline2").toString();
        assertEquals("\"a,b\",\"he said \"\"hi\"\"\",\"line1\nline2\"\r\n", out);
    }

    @Test
    void blankLineSeparatesSections() {
        String out = new CsvWriter().row("A").blankLine().row("B").toString();
        assertEquals("A\r\n\r\nB\r\n", out);
    }
}
