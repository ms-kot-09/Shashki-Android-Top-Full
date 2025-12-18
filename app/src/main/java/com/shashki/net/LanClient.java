package com.shashki.net;

import java.net.InetSocketAddress;
import java.net.Socket;

public class LanClient {
  public interface Listener {
    void onSocket(Socket socket);
    void onError(String err);
  }

  public void connect(String host, int port, Listener listener) {
    new Thread(() -> {
      try {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 4000);
        listener.onSocket(s);
      } catch (Exception e) {
        listener.onError(e.toString());
      }
    }, "LanClient").start();
  }
}
