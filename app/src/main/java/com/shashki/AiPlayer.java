package com.shashki;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AiPlayer {
  public enum Level { EASY, NORMAL, HARD }
  public Level level = Level.NORMAL;
  public boolean learningEnabled = true;

  private final Random rng = new Random();
  private final SharedPreferences prefs;

  // simple weights
  private float wMan, wKing, wMob;

  public AiPlayer(Context ctx) {
    prefs = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE);
    wMan  = prefs.getFloat("wMan", 1.0f);
    wKing = prefs.getFloat("wKing", 3.0f);
    wMob  = prefs.getFloat("wMob", 0.08f);
  }

  public void resetLearning() {
    wMan=1.0f; wKing=3.0f; wMob=0.08f;
    prefs.edit().clear().apply();
  }

  public Move pickMove(GameState s) {
    List<Move> moves = s.legalMoves();
    if (moves.isEmpty()) return null;

    // epsilon by level
    float eps;
    int depth;
    switch (level) {
      case EASY: eps = 0.35f; depth = 1; break;
      case HARD: eps = 0.05f; depth = 4; break;
      default:   eps = 0.15f; depth = 2; break;
    }

    if (rng.nextFloat() < eps) {
      return moves.get(rng.nextInt(moves.size())).copy();
    }

    float best = -1e9f;
    Move bestMove = moves.get(0);
    for (Move m : moves) {
      GameState t = cloneState(s);
      t.applyMove(m);
      float sc = -negamax(t, depth-1, -1e9f, 1e9f);
      // prefer captures a bit
      sc += m.captureCount() * 0.35f;
      if (sc > best) { best = sc; bestMove = m; }
    }
    return bestMove.copy();
  }

  private float negamax(GameState s, int depth, float alpha, float beta) {
    if (depth <= 0 || s.isGameOver()) {
      return evaluate(s);
    }
    List<Move> moves = s.legalMoves();
    if (moves.isEmpty()) return evaluate(s);

    float best = -1e9f;
    for (Move m : moves) {
      GameState t = cloneState(s);
      t.applyMove(m);
      float score = -negamax(t, depth-1, -beta, -alpha);
      if (score > best) best = score;
      if (best > alpha) alpha = best;
      if (alpha >= beta) break;
    }
    return best;
  }

  private float evaluate(GameState s) {
    int N = s.N;
    float score = 0f;
    for (int y=0;y<N;y++) for(int x=0;x<N;x++) {
      Piece p = s.at(x,y);
      if (p==Piece.W_MAN) score += wMan;
      if (p==Piece.W_KING) score += wKing;
      if (p==Piece.B_MAN) score -= wMan;
      if (p==Piece.B_KING) score -= wKing;
    }
    // mobility (legal move count of side to move)
    int mob = s.legalMoves().size();
    score += (s.whiteTurn ? +1 : -1) * mob * wMob;

    // from perspective of side to move
    return s.whiteTurn ? score : -score;
  }

  public void learnFromResult(int resultWhiteWin) {
    // +1 if white won, -1 if black won, 0 draw
    if (!learningEnabled) return;
    float r = resultWhiteWin;
    // tiny updates
    wMan  += r * 0.01f;
    wKing += r * 0.015f;
    wMob  += r * 0.001f;
    // clamp
    wMan  = clamp(wMan, 0.5f, 2.5f);
    wKing = clamp(wKing, 1.5f, 6.0f);
    wMob  = clamp(wMob, 0.01f, 0.25f);

    prefs.edit()
      .putFloat("wMan", wMan)
      .putFloat("wKing", wKing)
      .putFloat("wMob", wMob)
      .apply();
  }

  private float clamp(float v,float a,float b){ return Math.max(a, Math.min(b, v)); }

  private GameState cloneState(GameState s) {
    GameState t = new GameState(s.rules);
    t.importBoard(s.exportBoard(), s.whiteTurn);
    t.mustContinueChain = s.mustContinueChain;
    t.chainX = s.chainX; t.chainY = s.chainY;
    return t;
  }
}
