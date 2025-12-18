package com.shashki;

import java.util.ArrayList;
import java.util.List;

public class Move {
  public final int fx, fy, tx, ty;
  public final List<int[]> captures = new ArrayList<>(); // [x,y]
  public boolean promotes = false;

  public Move(int fx, int fy, int tx, int ty) {
    this.fx = fx; this.fy = fy; this.tx = tx; this.ty = ty;
  }

  public int captureCount() { return captures.size(); }

  public Move copy() {
    Move m = new Move(fx, fy, tx, ty);
    for (int[] c : captures) m.captures.add(new int[]{c[0], c[1]});
    m.promotes = promotes;
    return m;
  }
}
