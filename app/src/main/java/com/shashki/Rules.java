package com.shashki;

public class Rules {
  // Russian checkers defaults
  public int size = 8;
  public boolean mandatoryCapture = true;
  public boolean maxCaptureRule = false; // optional
  public boolean removeCapturedAtEndOfChain = false; // "Turkish strike" style; off for Russian
  public boolean manMovesForwardOnly = true;
  public boolean manCapturesBackward = true;
  public boolean kingFlying = true; // king moves any distance diagonally
  public boolean kingCaptureFlying = true; // king captures at distance
  public boolean promoteImmediate = true; // promote as soon as reaches back rank
  public boolean continueCaptureAsKingWhenPromoted = true;

  public static Rules russian() { return new Rules(); }
}
