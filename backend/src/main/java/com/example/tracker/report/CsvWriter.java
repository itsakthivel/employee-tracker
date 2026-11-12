package com.example.tracker.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RFC 4180 CSV with a UTF-8 BOM (so spreadsheets read non-ASCII names correctly) and formula
 * injection protection: cells starting with = + - @ are prefixed with an apostrophe.
 */
public class CsvWriter implements AutoCloseable {

  private final Writer out;

  public CsvWriter(OutputStream stream) throws IOException {
    stream.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
  }

  public void row(List<String> cells) throws IOException {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        out.write(',');
      }
      out.write(cell(cells.get(i)));
    }
    out.write("\r\n");
  }

  static String cell(String raw) {
    if (raw == null) {
      return "";
    }
    String v = raw;
    if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
      v = "'" + v;
    }
    if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
      v = "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  @Override
  public void close() throws IOException {
    out.flush();
  }
}
