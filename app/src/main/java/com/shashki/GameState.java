package com.shashki;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GameState {
  public final Rules rules;
  public final int N;
  public Piece[][] b;
  public boolean whiteTurn = true;

  // selection
  public int selX = -1, selY = -1;

  // capture chain state
  public boolean mustContinueChain = false;
  public int chainX = -1, chainY = -1;

  // undo
  public static class Snapshot {
    public Piece[][] b;
    public boolean whiteTurn;
    public boolean mustContinueChain;
    public int chainX, chainY;
  }
  private final Deque<Snapshot> undo = new ArrayDeque<>();

  public GameState(Rules rules) {
    this.rules = rules;
    this.N = rules.size;
    reset();
  }

  public void reset() {
    b = new Piece[N][N];
    for (int y=0;y<N;y++) for(int x=0;x<N;x++) b[y][x] = Piece.EMPTY;
    // place 12/12 on dark squares
    for (int y=0;y<3;y++) for(int x=0;x<N;x++) if (((x+y)&1)==1) b[y][x]=Piece.B_MAN;
    for (int y=N-3;y<N;y++) for(int x=0;x<N;x++) if (((x+y)&1)==1) b[y][x]=Piece.W_MAN;

    whiteTurn = true;
    clearSelection();
    mustContinueChain = false;
    chainX = chainY = -1;
    undo.clear();
  }

  public void clearSelection() { selX=selY=-1; }

  public boolean isPlayable(int x,int y) { return ((x+y)&1)==1; }

  public Piece at(int x,int y){ return b[y][x]; }
  public void set(int x,int y, Piece p){ b[y][x]=p; }

  public boolean in(int x,int y){ return x>=0 && y>=0 && x<N && y<N; }

  public boolean sideOwns(Piece p){
    if (p==Piece.EMPTY) return false;
    return whiteTurn ? p.isWhite() : p.isBlack();
  }

  public boolean sideEnemy(Piece p){
    if (p==Piece.EMPTY) return false;
    return whiteTurn ? p.isBlack() : p.isWhite();
  }

  private Snapshot snap() {
    Snapshot s = new Snapshot();
    s.b = new Piece[N][N];
    for (int y=0;y<N;y++) System.arraycopy(b[y], 0, s.b[y], 0, N);
    s.whiteTurn = whiteTurn;
    s.mustContinueChain = mustContinueChain;
    s.chainX = chainX; s.chainY = chainY;
    return s;
  }

  public boolean canUndo(){ return !undo.isEmpty(); }

  public void undo() {
    if (undo.isEmpty()) return;
    Snapshot s = undo.pop();
    this.b = s.b;
    this.whiteTurn = s.whiteTurn;
    this.mustContinueChain = s.mustContinueChain;
    this.chainX = s.chainX; this.chainY = s.chainY;
    clearSelection();
  }

  // --- move generation (Russian checkers) ---
  public List<Move> legalMoves() {
    List<Move> all = new ArrayList<>();
    if (mustContinueChain && in(chainX,chainY)) {
      all.addAll(capturesFrom(chainX, chainY));
      if (!all.isEmpty()) return all;
      // no more captures => chain ends
      mustContinueChain = false;
      chainX = chainY = -1;
    }

    // normal: gather all moves, but enforce mandatory capture
    List<Move> caps = new ArrayList<>();
    for (int y=0;y<N;y++) for(int x=0;x<N;x++){
      Piece p = at(x,y);
      if (!sideOwns(p)) continue;
      caps.addAll(capturesFrom(x,y));
    }
    if (rules.mandatoryCapture && !caps.isEmpty()) {
      if (rules.maxCaptureRule) {
        int mx=0; for(Move m:caps) mx=Math.max(mx, m.captureCount());
        List<Move> filt=new ArrayList<>();
        for(Move m:caps) if(m.captureCount()==mx) filt.add(m);
        return filt;
      }
      return caps;
    }

    for (int y=0;y<N;y++) for(int x=0;x<N;x++){
      Piece p = at(x,y);
      if (!sideOwns(p)) continue;
      all.addAll(quietMovesFrom(x,y));
    }
    return all;
  }

  public List<Move> legalMovesFor(int x,int y){
    List<Move> moves = legalMoves();
    List<Move> out = new ArrayList<>();
    for (Move m : moves) if (m.fx==x && m.fy==y) out.add(m);
    return out;
  }

  private List<Move> quietMovesFrom(int x,int y){
    List<Move> out = new ArrayList<>();
    Piece p = at(x,y);
    if (p==Piece.EMPTY) return out;
    int dir = p.isWhite() ? -1 : 1;

    int[][] dirs = new int[][]{{-1,-1},{1,-1},{-1,1},{1,1}};
    if (!p.isKing()) {
      for (int[] d : dirs) {
        if (rules.manMovesForwardOnly && d[1]!=dir) continue;
        int nx=x+d[0], ny=y+d[1];
        if (in(nx,ny) && isPlayable(nx,ny) && at(nx,ny)==Piece.EMPTY) out.add(new Move(x,y,nx,ny));
      }
    } else {
      if (!rules.kingFlying) {
        for(int[] d:dirs){
          int nx=x+d[0], ny=y+d[1];
          if (in(nx,ny) && isPlayable(nx,ny) && at(nx,ny)==Piece.EMPTY) out.add(new Move(x,y,nx,ny));
        }
      } else {
        for(int[] d:dirs){
          int nx=x+d[0], ny=y+d[1];
          while(in(nx,ny) && isPlayable(nx,ny) && at(nx,ny)==Piece.EMPTY){
            out.add(new Move(x,y,nx,ny));
            nx+=d[0]; ny+=d[1];
          }
        }
      }
    }
    return out;
  }

  private List<Move> capturesFrom(int x,int y){
    List<Move> out = new ArrayList<>();
    Piece p = at(x,y);
    if (p==Piece.EMPTY) return out;

    int dir = p.isWhite() ? -1 : 1;
    int[][] dirs = new int[][]{{-1,-1},{1,-1},{-1,1},{1,1}};

    if (!p.isKing()) {
      for (int[] d : dirs) {
        if (!rules.manCapturesBackward && rules.manMovesForwardOnly && d[1]!=dir) continue;
        int mx=x+d[0], my=y+d[1];
        int nx=x+2*d[0], ny=y+2*d[1];
        if (!in(nx,ny) || !isPlayable(nx,ny)) continue;
        if (sideEnemy(at(mx,my)) && at(nx,ny)==Piece.EMPTY) {
          Move m = new Move(x,y,nx,ny);
          m.captures.add(new int[]{mx,my});
          out.add(m);
        }
      }
    } else {
      if (!rules.kingFlying || !rules.kingCaptureFlying) {
        for (int[] d:dirs){
          int mx=x+d[0], my=y+d[1];
          int nx=x+2*d[0], ny=y+2*d[1];
          if (!in(nx,ny) || !isPlayable(nx,ny)) continue;
          if (sideEnemy(at(mx,my)) && at(nx,ny)==Piece.EMPTY) {
            Move m=new Move(x,y,nx,ny);
            m.captures.add(new int[]{mx,my});
            out.add(m);
          }
        }
      } else {
        // flying king capture: slide to enemy then empty squares beyond
        for(int[] d:dirs){
          int cx=x+d[0], cy=y+d[1];
          while(in(cx,cy) && isPlayable(cx,cy) && at(cx,cy)==Piece.EMPTY){
            cx+=d[0]; cy+=d[1];
          }
          if (!in(cx,cy) || !isPlayable(cx,cy)) continue;
          if (!sideEnemy(at(cx,cy))) continue;
          int ex=cx, ey=cy;
          int nx=ex+d[0], ny=ey+d[1];
          while(in(nx,ny) && isPlayable(nx,ny) && at(nx,ny)==Piece.EMPTY){
            Move m=new Move(x,y,nx,ny);
            m.captures.add(new int[]{ex,ey});
            out.add(m);
            nx+=d[0]; ny+=d[1];
          }
        }
      }
    }
    return out;
  }

  public boolean applyMove(Move m) {
    List<Move> legal = legalMoves();
    Move chosen = null;
    for (Move lm : legal) {
      if (lm.fx==m.fx && lm.fy==m.fy && lm.tx==m.tx && lm.ty==m.ty) { chosen = lm; break; }
    }
    if (chosen==null) return false;

    undo.push(snap());

    Piece p = at(chosen.fx, chosen.fy);
    set(chosen.fx, chosen.fy, Piece.EMPTY);
    set(chosen.tx, chosen.ty, p);

    // capture remove
    if (!chosen.captures.isEmpty()) {
      if (!rules.removeCapturedAtEndOfChain) {
        for (int[] c : chosen.captures) set(c[0], c[1], Piece.EMPTY);
      } // else we'll remove at end of chain (not used in Russian)
    }

    // promotion
    if (!p.isKing()) {
      if (p.isWhite() && chosen.ty==0) {
        set(chosen.tx, chosen.ty, Piece.W_KING);
        chosen.promotes = true;
      }
      if (p.isBlack() && chosen.ty==N-1) {
        set(chosen.tx, chosen.ty, Piece.B_KING);
        chosen.promotes = true;
      }
    }

    // chain capture
    if (!chosen.captures.isEmpty()) {
      // if promoted and rules says continue as king, keep piece as king (we already set it)
      mustContinueChain = true;
      chainX = chosen.tx; chainY = chosen.ty;

      // check if more captures exist
      List<Move> more = capturesFrom(chainX, chainY);
      if (more.isEmpty()) {
        mustContinueChain = false; chainX=chainY=-1;
        whiteTurn = !whiteTurn;
      }
    } else {
      whiteTurn = !whiteTurn;
    }

    clearSelection();
    return true;
  }

  public boolean isGameOver() {
    // if current side has no legal moves => loses
    return legalMoves().isEmpty();
  }

  public int winner() {
    // 1 white wins, -1 black wins, 0 none/draw
    if (!isGameOver()) return 0;
    // side to move has no moves -> loses
    return whiteTurn ? -1 : 1;
  }

  public int[] exportBoard() {
    int[] out = new int[N*N];
    int i=0;
    for (int y=0;y<N;y++) for(int x=0;x<N;x++){
      out[i++] = at(x,y).ordinal();
    }
    return out;
  }

  public void importBoard(int[] data, boolean whiteTurn) {
    int i=0;
    for (int y=0;y<N;y++) for(int x=0;x<N;x++){
      int v = data[i++];
      if (v<0 || v>=Piece.values().length) v = 0;
      b[y][x] = Piece.values()[v];
    }
    this.whiteTurn = whiteTurn;
    mustContinueChain = false;
    chainX = chainY = -1;
    clearSelection();
    undo.clear();
  }
}
