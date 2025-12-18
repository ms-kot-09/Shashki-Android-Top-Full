package com.shashki;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public class MainActivity extends Activity {
  private ShashkiView view;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    view = new ShashkiView(this);
    setContentView(view);
    hideSystemUI();
  }

  @Override protected void onResume() {
    super.onResume();
    view.onHostResume();
    hideSystemUI();
  }

  @Override protected void onPause() {
    view.onHostPause();
    super.onPause();
  }

  private void hideSystemUI() {
    View decor = getWindow().getDecorView();
    decor.post(() -> {
      WindowInsetsController c = decor.getWindowInsetsController();
      if (c != null) {
        c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      } else {
        decor.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
      }
    });
  }
}
