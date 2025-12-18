package com.shashki;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;

import com.shashki.audio.SoundManager;
import com.shashki.gfx.SpriteStore;
import com.shashki.net.LanClient;
import com.shashki.net.LanHost;
import com.shashki.net.LanLink;
import com.shashki.net.NetMessage;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShashkiView extends SurfaceView implements SurfaceHolder.Callback {
  private enum Screen { MENU, SETTINGS, GAME, NET_MENU, HOST_WAIT }
  private enum Mode { AI, HOTSEAT, LAN_HOST, LAN_CLIENT }

  private final Context ctx;
  private final SurfaceHolder holder;
  private Thread loop;
  private volatile boolean running = false;

  private final SpriteStore sprites;
  private final SoundManager sfx;
  private final SharedPreferences prefs;

  private boolean soundOn;
  private boolean reduceMotion;
  private AiPlayer.Level aiLevel;
  private boolean aiLearning;
  private int languageMode; // 0 system, 1 ru, 2 uk, 3 en
  private String lastJoinIp = "192.168.0.2";

  private Screen screen = Screen.MENU;
  private Mode mode = Mode.AI;
  private boolean playerIsWhite = true;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF r = new RectF();

  private int W,H;

  private RectF btnPlayAI, btnPlayHot, btnMulti, btnSettings;
  private RectF btnBack, btnReset, btnUndo, btnSound;
  private RectF btnHost, btnJoin;

  private RectF boardRect = new RectF();
  private float cell;

  private final GameState game;
  private final AiPlayer ai;

  private List<Move> selMoves = new ArrayList<>();

  // network
  private static final int PORT = 34567;
  private LanHost host = new LanHost();
  private LanClient client = new LanClient();
  private LanLink link = null;

  public ShashkiView(Context context) {
    super(context);
    this.ctx = context;
    holder = getHolder();
    holder.addCallback(this);

    sprites = new SpriteStore(context);
    sfx = new SoundManager(context);

    prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    soundOn = prefs.getBoolean("soundOn", true);
    reduceMotion = prefs.getBoolean("reduceMotion", true);
    aiLearning = prefs.getBoolean("aiLearning", true);
    int lvl = prefs.getInt("aiLevel", 1);
    aiLevel = (lvl==0)? AiPlayer.Level.EASY : (lvl==2? AiPlayer.Level.HARD : AiPlayer.Level.NORMAL);
    languageMode = prefs.getInt("lang", 0);
    lastJoinIp = prefs.getString("lastJoinIp", lastJoinIp);

    sfx.setEnabled(soundOn);

    ai = new AiPlayer(context);
    ai.level = aiLevel;
    ai.learningEnabled = aiLearning;

    game = new GameState(Rules.russian());

    setFocusable(true);
    setFocusableInTouchMode(true);
  }

  @Override public void surfaceCreated(SurfaceHolder holder) {
    running = true;
    loop = new Thread(this::loop, "RenderLoop");
    loop.start();
  }

  @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    W = width; H = height;
    layoutUI();
  }

  @Override public void surfaceDestroyed(SurfaceHolder holder) {
    running = false;
    if (loop != null) {
      try { loop.join(500); } catch (InterruptedException ignored) {}
    }
    stopNet();
    sfx.release();
    sprites.clear();
  }

  private void layoutUI() {
    float pad = dp(18);
    float bw = W * 0.62f;
    float bh = dp(110);

    btnPlayAI = new RectF((W-bw)/2f, H*0.42f, (W+bw)/2f, H*0.42f + bh);
    btnPlayHot = new RectF((W-bw)/2f, btnPlayAI.bottom + pad, (W+bw)/2f, btnPlayAI.bottom + pad + bh);
    btnMulti = new RectF((W-bw)/2f, btnPlayHot.bottom + pad, (W+bw)/2f, btnPlayHot.bottom + pad + bh);
    btnSettings = new RectF((W-bw)/2f, btnMulti.bottom + pad, (W+bw)/2f, btnMulti.bottom + pad + bh);

    float topH = dp(120);
    float bottomH = dp(140);

    float boardTop = topH + dp(10);
    float boardBottom = H - bottomH - dp(10);

    float boardSize = Math.min(W - dp(40), boardBottom - boardTop);
    float bx = (W - boardSize)/2f;
    float by = boardTop + ((boardBottom - boardTop) - boardSize)/2f;

    boardRect.set(bx, by, bx+boardSize, by+boardSize);
    cell = boardSize / game.N;

    float bbtn = dp(96);
    float y = H - bottomH/2f - bbtn/2f;

    btnBack = new RectF(dp(16), y, dp(16)+bbtn, y+bbtn);
    btnUndo = new RectF(btnBack.right + dp(16), y, btnBack.right + dp(16)+bbtn, y+bbtn);
    btnReset = new RectF(btnUndo.right + dp(16), y, btnUndo.right + dp(16)+bbtn, y+bbtn);
    btnSound = new RectF(W - dp(16)-bbtn, y, W - dp(16), y+bbtn);

    float nbw = W*0.62f, nbh = dp(110);
    btnHost = new RectF((W-nbw)/2f, H*0.50f, (W+nbw)/2f, H*0.50f + nbh);
    btnJoin = new RectF((W-nbw)/2f, btnHost.bottom + pad, (W+nbw)/2f, btnHost.bottom + pad + nbh);
  }

  private float dp(float v){
    float d = getResources().getDisplayMetrics().density;
    return v*d;
  }

  @Override public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
    float x = e.getX(), y = e.getY();
    sfx.tap();

    switch (screen) {
      case MENU:
        if (btnPlayAI.contains(x,y)) { startVsAi(); return true; }
        if (btnPlayHot.contains(x,y)) { startHotseat(); return true; }
        if (btnMulti.contains(x,y)) { screen = Screen.NET_MENU; return true; }
        if (btnSettings.contains(x,y)) { screen = Screen.SETTINGS; return true; }
        break;

      case NET_MENU:
        if (btnBack.contains(x,y)) { screen = Screen.MENU; return true; }
        if (btnHost.contains(x,y)) { startLanHost(); return true; }
        if (btnJoin.contains(x,y)) { promptJoinIp(); return true; }
        break;

      case HOST_WAIT:
        if (btnBack.contains(x,y)) { stopNet(); screen = Screen.NET_MENU; return true; }
        break;

      case SETTINGS:
        if (btnBack.contains(x,y)) { screen = Screen.MENU; saveSettings(); return true; }
        float sx = W*0.08f, sy = H*0.32f, row = dp(90);
        if (rowHit(x,y,sx,sy,row,0)) { toggleSound(); return true; }
        if (rowHit(x,y,sx,sy,row,1)) { cycleAiLevel(); return true; }
        if (rowHit(x,y,sx,sy,row,2)) { aiLearning = !aiLearning; ai.learningEnabled = aiLearning; return true; }
        if (rowHit(x,y,sx,sy,row,3)) { reduceMotion = !reduceMotion; return true; }
        if (rowHit(x,y,sx,sy,row,4)) { cycleLanguage(); return true; }
        if (rowHit(x,y,sx,sy,row,5)) { ai.resetLearning(); return true; }
        break;

      case GAME:
        if (btnBack.contains(x,y)) { stopNet(); screen = Screen.MENU; return true; }
        if (btnReset.contains(x,y)) { resetGame(); if (link!=null) link.sendLine(NetMessage.RESET); return true; }
        if (btnUndo.contains(x,y)) { if (game.canUndo()) game.undo(); return true; }
        if (btnSound.contains(x,y)) { toggleSound(); return true; }
        if (boardRect.contains(x,y)) { onBoardTap(x,y); return true; }
        break;
    }
    return true;
  }

  private boolean rowHit(float x,float y,float sx,float sy,float row,float idx){
    RectF rr = new RectF(sx, sy+row*idx, W-sx, sy+row*idx+dp(74));
    return rr.contains(x,y);
  }

  private void toggleSound() { soundOn = !soundOn; sfx.setEnabled(soundOn); }
  private void cycleAiLevel() {
    if (aiLevel == AiPlayer.Level.EASY) aiLevel = AiPlayer.Level.NORMAL;
    else if (aiLevel == AiPlayer.Level.NORMAL) aiLevel = AiPlayer.Level.HARD;
    else aiLevel = AiPlayer.Level.EASY;
    ai.level = aiLevel;
  }
  private void cycleLanguage() { languageMode = (languageMode + 1) % 4; }

  private void saveSettings() {
    int lvl = (aiLevel==AiPlayer.Level.EASY)?0: (aiLevel==AiPlayer.Level.HARD?2:1);
    prefs.edit()
      .putBoolean("soundOn", soundOn)
      .putBoolean("reduceMotion", reduceMotion)
      .putInt("aiLevel", lvl)
      .putBoolean("aiLearning", aiLearning)
      .putInt("lang", languageMode)
      .putString("lastJoinIp", lastJoinIp)
      .apply();
  }

  private String tr(String ru, String uk, String en) {
    int m = languageMode;
    if (m==0) {
      String lang = Locale.getDefault().getLanguage();
      if ("uk".equals(lang)) m=2;
      else if ("en".equals(lang)) m=3;
      else m=1;
    }
    if (m==2) return uk;
    if (m==3) return en;
    return ru;
  }

  private void startVsAi() {
    mode = Mode.AI;
    playerIsWhite = true;
    resetGame();
    screen = Screen.GAME;
  }

  private void startHotseat() {
    mode = Mode.HOTSEAT;
    resetGame();
    screen = Screen.GAME;
  }

  private void resetGame() {
    game.reset();
    game.clearSelection();
    selMoves.clear();
  }

  private void onBoardTap(float sx, float sy) {
    // turn gating
    if (mode==Mode.AI) {
      boolean myTurn = (playerIsWhite == game.whiteTurn);
      if (!myTurn) { sfx.error(); return; }
    }
    if (mode==Mode.LAN_HOST || mode==Mode.LAN_CLIENT) {
      boolean myTurn = (mode==Mode.LAN_HOST) ? game.whiteTurn : !game.whiteTurn;
      if (!myTurn) { sfx.error(); return; }
    }

    int x = (int)((sx - boardRect.left)/cell);
    int y = (int)((sy - boardRect.top)/cell);
    if (!game.in(x,y) || !game.isPlayable(x,y)) return;

    if (game.selX < 0) {
      Piece pc = game.at(x,y);
      if (!game.sideOwns(pc)) { sfx.error(); return; }
      List<Move> ms = game.legalMovesFor(x,y);
      if (ms.isEmpty()) { sfx.error(); return; }
      game.selX = x; game.selY = y;
      selMoves = ms;
      return;
    }

    // pick move
    Move chosen = null;
    for (Move m : selMoves) if (m.tx==x && m.ty==y) { chosen = m; break; }

    if (chosen == null) {
      // reselect if own piece
      Piece pc = game.at(x,y);
      if (game.sideOwns(pc)) {
        List<Move> ms = game.legalMovesFor(x,y);
        if (!ms.isEmpty()) { game.selX=x; game.selY=y; selMoves=ms; return; }
      }
      game.clearSelection();
      selMoves.clear();
      return;
    }

    boolean wasCap = chosen.captureCount() > 0;
    if (!game.applyMove(chosen)) { sfx.error(); return; }
    if (wasCap) sfx.capture(); else sfx.move();

    if (mode==Mode.LAN_HOST || mode==Mode.LAN_CLIENT) sendMove(chosen);

    if (game.isGameOver()) {
      int w = game.winner();
      if (mode==Mode.AI) ai.learnFromResult(w);
      sfx.win();
      return;
    }

    if (mode==Mode.AI) {
      boolean aiTurn = (playerIsWhite != game.whiteTurn);
      if (aiTurn) scheduleAiMove();
    }
  }

  private void scheduleAiMove() {
    new Thread(() -> {
      SystemClock.sleep(reduceMotion ? 200 : 90);
      Move m = ai.pickMove(game);
      if (m == null) return;
      post(() -> {
        boolean cap = m.captureCount()>0;
        if (game.applyMove(m)) {
          if (cap) sfx.capture(); else sfx.move();
          if (game.isGameOver()) {
            int w = game.winner();
            ai.learnFromResult(w);
            sfx.win();
          }
        }
      });
    }, "AI").start();
  }

  // --- LAN ---
  private void startLanHost() {
    stopNet();
    mode = Mode.LAN_HOST;
    resetGame();
    screen = Screen.HOST_WAIT;

    host.start(PORT, new LanHost.Listener() {
      @Override public void onClientSocket(Socket socket) {
        try {
          link = new LanLink(socket, new LanLink.Listener() {
            @Override public void onLine(String line) { onNetLine(line); }
            @Override public void onClosed(String reason) { post(ShashkiView.this::stopNet); }
          });
          post(() -> { screen = Screen.GAME; sendState(); });
        } catch (Exception e) {
          post(() -> { stopNet(); screen = Screen.NET_MENU; });
        }
      }
      @Override public void onError(String err) { post(() -> { stopNet(); screen = Screen.NET_MENU; }); }
    });
  }

  private void promptJoinIp() {
    ((Activity)ctx).runOnUiThread(() -> {
      EditText input = new EditText(ctx);
      input.setInputType(InputType.TYPE_CLASS_TEXT);
      input.setText(lastJoinIp);
      new AlertDialog.Builder(ctx)
        .setTitle(tr("Подключиться","Підключитися","Join"))
        .setMessage(tr("IP хоста:","IP хоста:","Host IP:"))
        .setView(input)
        .setPositiveButton(tr("ОК","ОК","OK"), (d,w) -> {
          lastJoinIp = input.getText().toString().trim();
          saveSettings();
          startLanJoin(lastJoinIp);
        })
        .setNegativeButton(tr("Отмена","Скасувати","Cancel"), null)
        .show();
    });
  }

  private void startLanJoin(String ip) {
    stopNet();
    mode = Mode.LAN_CLIENT;
    resetGame();
    screen = Screen.HOST_WAIT;

    client.connect(ip, PORT, new LanClient.Listener() {
      @Override public void onSocket(Socket socket) {
        try {
          link = new LanLink(socket, new LanLink.Listener() {
            @Override public void onLine(String line) { onNetLine(line); }
            @Override public void onClosed(String reason) { post(ShashkiView.this::stopNet); }
          });
          post(() -> { screen = Screen.GAME; link.sendLine(NetMessage.HELLO); });
        } catch (Exception e) {
          post(() -> { stopNet(); screen = Screen.NET_MENU; });
        }
      }
      @Override public void onError(String err) { post(() -> { stopNet(); screen = Screen.NET_MENU; }); }
    });
  }

  private void onNetLine(String line) {
    if (line == null) return;
    if (line.startsWith(NetMessage.STATE+" ")) {
      String payload = line.substring((NetMessage.STATE+" ").length());
      String[] parts = payload.split("\\|", 2);
      if (parts.length != 2) return;
      boolean wt = "1".equals(parts[0]);
      String[] nums = parts[1].split(",");
      int[] data = new int[game.N*game.N];
      for (int i=0;i<data.length && i<nums.length;i++){
        try { data[i] = Integer.parseInt(nums[i]); } catch (Exception ignored) { data[i]=0; }
      }
      post(() -> game.importBoard(data, wt));
      return;
    }
    if (line.startsWith(NetMessage.MOVE+" ")) {
      String[] a = line.split(" ");
      if (a.length < 5) return;
      try {
        int fx=Integer.parseInt(a[1]), fy=Integer.parseInt(a[2]), tx=Integer.parseInt(a[3]), ty=Integer.parseInt(a[4]);
        post(() -> {
          Move m = new Move(fx,fy,tx,ty);
          boolean ok = game.applyMove(m);
          if (ok) sfx.move(); else sfx.error();
          if (game.isGameOver()) sfx.win();
        });
      } catch (Exception ignored) {}
      return;
    }
    if (line.startsWith(NetMessage.RESET)) {
      post(this::resetGame);
    }
  }

  private void sendState() {
    if (link == null) return;
    int[] data = game.exportBoard();
    StringBuilder sb = new StringBuilder();
    sb.append(NetMessage.STATE).append(" ").append(game.whiteTurn ? "1" : "0").append("|");
    for (int i=0;i<data.length;i++){
      if (i>0) sb.append(",");
      sb.append(data[i]);
    }
    link.sendLine(sb.toString());
  }

  private void sendMove(Move m) {
    if (link == null) return;
    link.sendLine(NetMessage.MOVE+" "+m.fx+" "+m.fy+" "+m.tx+" "+m.ty);
  }

  private void stopNet() {
    if (link != null) { link.close(); link = null; }
    host.stop();
  }

  // --- render loop ---
  private void loop() {
    long last = SystemClock.uptimeMillis();
    while (running) {
      long now = SystemClock.uptimeMillis();
      if (now - last < 16) { SystemClock.sleep(1); continue; }
      last = now;
      Canvas c = null;
      try {
        c = holder.lockCanvas();
        if (c == null) continue;
        render(c);
      } finally {
        if (c != null) holder.unlockCanvasAndPost(c);
      }
    }
  }

  private void render(Canvas c) {
    // background (cover)
    Bitmap bg = sprites.get("sprites/bg/bg_game.png");
    drawCover(c, bg, new RectF(0,0,W,H));

    switch (screen) {
      case MENU: drawMenu(c); break;
      case SETTINGS: drawSettings(c); break;
      case NET_MENU: drawNetMenu(c); break;
      case HOST_WAIT: drawHostWait(c); break;
      case GAME: drawGame(c); break;
    }
  }

  private void drawMenu(Canvas c) {
    Bitmap panel = sprites.get("sprites/ui/panels/panel_modal.png");
    draw9(c, panel, new RectF(W*0.08f, H*0.12f, W*0.92f, H*0.86f));

    drawTitle(c, tr("ШАШКИ","ШАШКИ","CHECKERS"), H*0.22f);

    drawButton(c, btnPlayAI, sprites.get("sprites/ui/buttons/btn_play.png"), tr("Играть vs ИИ","Грати vs AI","Play vs AI"));
    drawButton(c, btnPlayHot, sprites.get("sprites/ui/buttons/btn_play.png"), tr("Игра вдвоём","Гра удвох","Hotseat"));
    drawButton(c, btnMulti, sprites.get("sprites/ui/buttons/btn_multiplayer.png"), tr("Мультиплеер","Мультиплеєр","Multiplayer"));
    drawButton(c, btnSettings, sprites.get("sprites/ui/buttons/btn_settings.png"), tr("Настройки","Налаштування","Settings"));
  }

  private void drawNetMenu(Canvas c) {
    Bitmap panel = sprites.get("sprites/ui/panels/panel_modal.png");
    draw9(c, panel, new RectF(W*0.08f, H*0.12f, W*0.92f, H*0.86f));
    drawTitle(c, tr("Мультиплеер","Мультиплеєр","Multiplayer"), H*0.22f);

    drawIconButton(c, btnBack, sprites.get("sprites/ui/buttons/btn_back.png"));

    drawButton(c, btnHost, sprites.get("sprites/ui/buttons/btn_host.png"), tr("Создать игру","Створити гру","Host"));
    drawButton(c, btnJoin, sprites.get("sprites/ui/buttons/btn_join.png"), tr("Подключиться","Підключитися","Join"));
  }

  private void drawHostWait(Canvas c) {
    Bitmap panel = sprites.get("sprites/ui/panels/panel_modal.png");
    draw9(c, panel, new RectF(W*0.08f, H*0.18f, W*0.92f, H*0.82f));

    drawIconButton(c, btnBack, sprites.get("sprites/ui/buttons/btn_back.png"));

    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(dp(44));
    c.drawText(tr("Ждём подключение…","Чекаємо підключення…","Waiting for client…"), W/2f, H*0.45f, paint);

    paint.setTextSize(dp(30));
    c.drawText(tr("Порт: 34567","Порт: 34567","Port: 34567"), W/2f, H*0.52f, paint);
    c.drawText(tr("Join по IP","Join по IP","Join by IP"), W/2f, H*0.58f, paint);
  }

  private void drawSettings(Canvas c) {
    Bitmap panel = sprites.get("sprites/ui/panels/panel_modal.png");
    draw9(c, panel, new RectF(W*0.06f, H*0.10f, W*0.94f, H*0.90f));
    drawIconButton(c, btnBack, sprites.get("sprites/ui/buttons/btn_back.png"));
    drawTitle(c, tr("Настройки","Налаштування","Settings"), H*0.18f);

    float sx = W*0.10f, sy = H*0.28f, row = dp(90);
    drawSettingRow(c, sx, sy+row*0, tr("Звук","Звук","Sound"), soundOn ? tr("Вкл","Увімк","On") : tr("Выкл","Вимк","Off"));
    drawSettingRow(c, sx, sy+row*1, tr("Сложность ИИ","Складність AI","AI level"),
      aiLevel== AiPlayer.Level.EASY? tr("Лёгкая","Легка","Easy") :
      (aiLevel== AiPlayer.Level.HARD? tr("Сложная","Складна","Hard") : tr("Нормальная","Нормальна","Normal")));
    drawSettingRow(c, sx, sy+row*2, tr("Обучение ИИ","Навчання AI","AI learning"), aiLearning ? tr("Вкл","Увімк","On") : tr("Выкл","Вимк","Off"));
    drawSettingRow(c, sx, sy+row*3, tr("Меньше анимаций","Менше анімацій","Reduce motion"), reduceMotion ? tr("Да","Так","Yes") : tr("Нет","Ні","No"));
    drawSettingRow(c, sx, sy+row*4, tr("Язык","Мова","Language"),
      languageMode==0? tr("Система","Система","System") :
      (languageMode==1? "Русский" : (languageMode==2? "Українська" : "English")));
    drawSettingRow(c, sx, sy+row*5, tr("Сбросить обучение","Скинути навчання","Reset learning"), tr("Нажми","Натисни","Tap"));
  }

  private void drawGame(Canvas c) {
    // top panel (opaque)
    draw9(c, sprites.get("sprites/ui/panels/panel_top.png"), new RectF(0,0,W,dp(120)));

    // board underlay (opaque, no background visible)
    RectF under = new RectF(boardRect.left-dp(18), boardRect.top-dp(18), boardRect.right+dp(18), boardRect.bottom+dp(18));
    draw9(c, sprites.get("sprites/board/board_underlay.png"), under);

    drawBoard(c);

    // frame
    RectF frame = new RectF(boardRect.left-dp(12), boardRect.top-dp(12), boardRect.right+dp(12), boardRect.bottom+dp(12));
    draw9(c, sprites.get("sprites/board/board_frame.png"), frame);

    // bottom panel
    draw9(c, sprites.get("sprites/ui/panels/panel_bottom.png"), new RectF(0,H-dp(140),W,H));

    drawIconButton(c, btnBack, sprites.get("sprites/ui/buttons/btn_back.png"));
    drawIconButton(c, btnUndo, sprites.get("sprites/ui/buttons/btn_undo.png"));
    drawIconButton(c, btnReset, sprites.get("sprites/ui/buttons/btn_reset.png"));
    drawIconButton(c, btnSound, soundOn ?
      sprites.get("sprites/ui/buttons/btn_sound_on.png") :
      sprites.get("sprites/ui/buttons/btn_sound_off.png"));

    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setTextSize(dp(34));

    c.drawText(game.whiteTurn ? tr("Ход: белые","Хід: білі","Turn: White") : tr("Ход: чёрные","Хід: чорні","Turn: Black"),
      dp(22), dp(78), paint);

    String modeTxt = (mode==Mode.AI) ? tr("Режим: ИИ","Режим: AI","Mode: AI") :
      (mode==Mode.HOTSEAT) ? tr("Режим: вдвоём","Режим: удвох","Mode: Hotseat") :
      (mode==Mode.LAN_HOST) ? tr("LAN: хост","LAN: хост","LAN: host") :
      tr("LAN: клиент","LAN: клієнт","LAN: client");
    c.drawText(modeTxt, dp(22), dp(112), paint);

    if (game.isGameOver()) {
      int w = game.winner();
      String win = (w==1) ? tr("Победа белых!","Перемога білих!","White wins!") :
                   (w==-1)? tr("Победа чёрных!","Перемога чорних!","Black wins!") :
                           tr("Ничья","Нічия","Draw");
      RectF modal = new RectF(W*0.10f, H*0.40f, W*0.90f, H*0.62f);
      draw9(c, sprites.get("sprites/ui/panels/panel_modal.png"), modal);
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(dp(44));
      c.drawText(win, W/2f, H*0.52f, paint);
    }
  }

  private void drawBoard(Canvas c) {
    Bitmap tileDark = sprites.get("sprites/board/tile_dark.png");
    Bitmap tileLight = sprites.get("sprites/board/tile_light.png");
    Bitmap hlMove = sprites.get("sprites/effects/hl_move.png");
    Bitmap hlCap = sprites.get("sprites/effects/hl_capture.png");
    Bitmap hlSel = sprites.get("sprites/effects/hl_selected.png");

    for (int y=0;y<game.N;y++) {
      for (int x=0;x<game.N;x++) {
        float lx = boardRect.left + x*cell;
        float ty = boardRect.top + y*cell;
        r.set(lx, ty, lx+cell, ty+cell);
        boolean dark = ((x+y)&1)==1;
        c.drawBitmap(dark?tileDark:tileLight, null, r, null);

        if (!dark) continue;

        // selected source
        if (game.selX==x && game.selY==y) c.drawBitmap(hlSel, null, r, null);

        // destinations
        if (game.selX>=0) {
          for (Move m : selMoves) {
            if (m.tx==x && m.ty==y) {
              c.drawBitmap(m.captureCount()>0 ? hlCap : hlMove, null, r, null);
            }
          }
        }
      }
    }

    Bitmap mw = sprites.get("sprites/pieces/man_white.png");
    Bitmap mb = sprites.get("sprites/pieces/man_black.png");
    Bitmap kw = sprites.get("sprites/pieces/king_white.png");
    Bitmap kb = sprites.get("sprites/pieces/king_black.png");

    for (int y=0;y<game.N;y++) for (int x=0;x<game.N;x++) {
      Piece pc = game.at(x,y);
      if (pc==Piece.EMPTY) continue;
      float lx = boardRect.left + x*cell;
      float ty = boardRect.top + y*cell;
      r.set(lx, ty, lx+cell, ty+cell);
      RectF pr = new RectF(r.left+cell*0.08f, r.top+cell*0.08f, r.right-cell*0.08f, r.bottom-cell*0.08f);
      Bitmap spr = (pc==Piece.W_MAN)?mw : (pc==Piece.B_MAN)?mb : (pc==Piece.W_KING)?kw : kb;
      c.drawBitmap(spr, null, pr, null);
    }
  }

  // --- widgets ---
  private void drawTitle(Canvas c, String title, float y) {
    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(dp(64));
    c.drawText(title, W/2f, y, paint);
  }

  private void drawButton(Canvas c, RectF rr, Bitmap icon, String label) {
    Bitmap btn = sprites.get("sprites/ui/buttons/btn_glass.png");
    c.drawBitmap(btn, null, rr, null);
    RectF ir = new RectF(rr.left+dp(18), rr.top+dp(18), rr.left+dp(18)+dp(74), rr.top+dp(18)+dp(74));
    c.drawBitmap(icon, null, ir, null);

    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setTextSize(dp(38));
    c.drawText(label, rr.left+dp(110), rr.centerY()+dp(14), paint);
  }

  private void drawIconButton(Canvas c, RectF rr, Bitmap icon) {
    Bitmap btn = sprites.get("sprites/ui/buttons/btn_round.png");
    c.drawBitmap(btn, null, rr, null);
    RectF ir = new RectF(rr.left+rr.width()*0.18f, rr.top+rr.height()*0.18f, rr.right-rr.width()*0.18f, rr.bottom-rr.height()*0.18f);
    c.drawBitmap(icon, null, ir, null);
  }

  private void drawSettingRow(Canvas c, float x, float y, String name, String value) {
    RectF rr = new RectF(x, y, W-x, y+dp(74));
    Bitmap bar = sprites.get("sprites/ui/panels/setting_row.png");
    c.drawBitmap(bar, null, rr, null);

    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setTextSize(dp(34));
    c.drawText(name, x+dp(20), y+dp(50), paint);

    paint.setTextAlign(Paint.Align.RIGHT);
    c.drawText(value, W-x-dp(20), y+dp(50), paint);
  }

  // draw bitmap covering rect with crop (like CSS cover)
  private void drawCover(Canvas c, Bitmap b, RectF dst) {
    if (b == null) return;
    float bw = b.getWidth(), bh = b.getHeight();
    float dw = dst.width(), dh = dst.height();
    float s = Math.max(dw/bw, dh/bh);
    float sw = dw/s, sh = dh/s;
    float sx = (bw - sw)/2f;
    float sy = (bh - sh)/2f;
    Rect src = new Rect((int)sx, (int)sy, (int)(sx+sw), (int)(sy+sh));
    c.drawBitmap(b, src, dst, null);
  }

  // 9-slice-ish: for our panels we keep it simple (scale to rect)
  private void draw9(Canvas c, Bitmap b, RectF dst) {
    c.drawBitmap(b, null, dst, null);
  }
}
