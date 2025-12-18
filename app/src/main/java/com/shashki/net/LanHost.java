package com.shashki.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LanHost {
  public interface Listener {
    void onClientSocket(Socket socket);
    void onError(String err);
  }

  private ServerSocket server;
  private Thread thread;

  public void start(int port, Listener listener) {
    stop();
    thread = new Thread(() -> {
      try {
        server = new ServerSocket(port);
        Socket s = server.accept(); // one client
        listener.onClientSocket(s);
      } catch (Exception e) {
        listener.onError(e.toString());
      } finally {
        stop();
      }
    }, "LanHost");
    thread.start();
  }

  public void stop() {
    try { if (server != null) server.close(); } catch (IOException ignored) {}
    server = null;
    thread = null;
  }
}
