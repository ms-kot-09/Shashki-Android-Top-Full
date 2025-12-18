package com.shashki.net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class LanLink {
  public interface Listener {
    void onLine(String line);
    void onClosed(String reason);
  }

  private final Socket socket;
  private final BufferedReader in;
  private final BufferedWriter out;
  private final Thread rxThread;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public LanLink(Socket socket, Listener listener) throws IOException {
    this.socket = socket;
    this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

    rxThread = new Thread(() -> {
      try {
        String line;
        while (!closed.get() && (line = in.readLine()) != null) {
          listener.onLine(line);
        }
        if (!closed.get()) listener.onClosed("eof");
      } catch (Exception e) {
        if (!closed.get()) listener.onClosed(e.toString());
      } finally {
        closeQuiet();
      }
    }, "LanLink-RX");
    rxThread.start();
  }

  public synchronized void sendLine(String line) {
    if (closed.get()) return;
    try {
      out.write(line);
      out.write("\n");
      out.flush();
    } catch (Exception ignored) {
      close();
    }
  }

  public void close() {
    if (closed.compareAndSet(false, true)) {
      closeQuiet();
    }
  }

  private void closeQuiet() {
    try { socket.close(); } catch (Exception ignored) {}
    try { in.close(); } catch (Exception ignored) {}
    try { out.close(); } catch (Exception ignored) {}
  }
}
