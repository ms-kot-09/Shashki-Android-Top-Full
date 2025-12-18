package com.shashki.gfx;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.HashMap;

public class SpriteStore {
  private final AssetManager am;
  private final HashMap<String, Bitmap> cache = new HashMap<>();

  public SpriteStore(Context ctx) {
    am = ctx.getAssets();
  }

  public Bitmap get(String path) {
    Bitmap b = cache.get(path);
    if (b != null) return b;
    try (InputStream is = am.open(path)) {
      BitmapFactory.Options opt = new BitmapFactory.Options();
      opt.inScaled = false;
      opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
      b = BitmapFactory.decodeStream(is, null, opt);
      cache.put(path, b);
      return b;
    } catch (Exception e) {
      // return tiny fallback (never null)
      Bitmap fb = Bitmap.createBitmap(2,2, Bitmap.Config.ARGB_8888);
      fb.eraseColor(0xFFFF00FF); // magenta
      cache.put(path, fb);
      return fb;
    }
  }

  public void clear() {
    for (Bitmap b : cache.values()) { b.recycle(); }
    cache.clear();
  }
}
