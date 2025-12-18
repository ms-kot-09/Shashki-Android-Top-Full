package com.shashki.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.shashki.R;

public class SoundManager {
  private final SoundPool pool;
  private boolean enabled = true;

  private int sTap, sMove, sCapture, sWin, sError;

  public SoundManager(Context ctx) {
    AudioAttributes attrs = new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_GAME)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build();
    pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
    sTap = pool.load(ctx, R.raw.tap, 1);
    sMove = pool.load(ctx, R.raw.move, 1);
    sCapture = pool.load(ctx, R.raw.capture, 1);
    sWin = pool.load(ctx, R.raw.win, 1);
    sError = pool.load(ctx, R.raw.error, 1);
  }

  public void setEnabled(boolean v){ enabled = v; }

  public void tap(){ play(sTap); }
  public void move(){ play(sMove); }
  public void capture(){ play(sCapture); }
  public void win(){ play(sWin); }
  public void error(){ play(sError); }

  private void play(int id){
    if (!enabled) return;
    pool.play(id, 1f, 1f, 1, 0, 1f);
  }

  public void release(){ pool.release(); }
}
