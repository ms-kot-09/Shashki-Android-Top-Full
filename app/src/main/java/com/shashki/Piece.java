package com.shashki;

public enum Piece {
  EMPTY,
  W_MAN, W_KING,
  B_MAN, B_KING;

  public boolean isWhite() { return this == W_MAN || this == W_KING; }
  public boolean isBlack() { return this == B_MAN || this == B_KING; }
  public boolean isKing()  { return this == W_KING || this == B_KING; }
}
