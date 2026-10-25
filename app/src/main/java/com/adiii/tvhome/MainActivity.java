package com.adiii.tvhome;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apple TV style home screen.
 * Everything is drawn from code. Focus is handled by our own small model
 * (top bar / dock / grid) so D-pad movement is always predictable.
 */
public class MainActivity extends Activity {

    static final int Z_TOP = 0, Z_DOCK = 1, Z_GRID = 2;
    static final int MAX_DOCK = 7, MIN_COLS = 4;
    static final float FOCUS_SCALE = 1.08f;
    static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.mitv.tvhome.mitvplus"
    };

    static class App {
        String pkg, label;
        ComponentName cn;
        Drawable art;
        boolean isBanner;
        int glow;
        boolean glowDone;
    }

    /** One app tile: rounded banner (or icon on a dark card). */
    static class TileView extends View {
        final App app;
        final Path clip = new Path();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float radius;

        TileView(Context c, App a, float radius) {
            super(c);
            this.app = a;
            this.radius = radius;
            setFocusable(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.save();
            c.clipPath(clip);
            if (app.art != null && app.isBanner) {
                app.art.setBounds(0, 0, w, h);
                app.art.draw(c);
            } else {
                paint.setColor(0xFF2C2C2E);
                c.drawRect(0, 0, w, h, paint);
                if (app.art != null) {
                    int s = (int) (Math.min(w, h) * 0.58f);
                    int l = (w - s) / 2, t = (h - s) / 2;
                    app.art.setBounds(l, t, l + s, t + s);
                    app.art.draw(c);
                }
            }
            c.restore();
        }
    }

    SharedPreferences prefs;
    float density;
    int W, H, margin, gap;
    Typeface fThin, fLight, fReg;

    FrameLayout root, content, header;
    View band, glowView, highlight;
    GradientDrawable glowDrawable;
    int glowColor = 0;
    TextView bigName, tileLabel, greeting;
    TextView[] pills = new TextView[3];
    TextClock clock, date;
    LinearLayout dropdown;
    boolean dropdownOpen;
    int ddIdx;
    final List<TextView> ddRows = new ArrayList<>();
    final List<String> ddIds = new ArrayList<>();

    List<App> all = new ArrayList<>();
    List<App> dock = new ArrayList<>();
    List<App> grid = new ArrayList<>();
    final List<TileView> dockTiles = new ArrayList<>();
    final List<TileView> gridTiles = new ArrayList<>();
    TileView focusedTile;

    int zone = Z_DOCK, dockIdx = 0, gridIdx = 0, pillIdx = 0;
    String focusPkg;
    int cols = MIN_COLS, tileW, tileH, bandTop, dockY, row0Top, rowH, curScroll;
    boolean built, loading, longHandled, highlightShown;
    String sig0 = "";

    // ------------------------------------------------------------ setup

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        W = getResources().getDisplayMetrics().widthPixels;
        H = getResources().getDisplayMetrics().heightPixels;
        margin = dp(64);
        gap = dp(16);
        fThin = font("Outfit-Thin.ttf", Typeface.create("sans-serif-thin", Typeface.NORMAL));
        fLight = font("Outfit-Light.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL));
        fReg = font("Outfit-Regular.ttf", Typeface.create("sans-serif", Typeface.NORMAL));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(W, H));
        header = new FrameLayout(this);
        root.addView(header, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildHeader();
        buildDropdown();
        setContentView(root);

        focusPkg = prefs.getString("lastPkg", null);
        askLocationOnce();
    }

    Typeface font(String file, Typeface fallback) {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/" + file);
        } catch (Exception e) {
            return fallback;
        }
    }

    int dp(float v) {
        return Math.round(v * density);
    }

    TextView tv(float sp, Typeface f, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTypeface(f);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setFocusable(false);
        return t;
    }

    void buildHeader() {
        greeting = tv(18, fReg, 0xB3FFFFFF);
        addHeader(greeting, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                margin, dp(32));

        clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTypeface(fThin);
        clock.setTextSize(88);
        clock.setTextColor(Color.WHITE);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        addHeader(clock, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                margin - dp(4), dp(58));

        date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTypeface(fReg);
        date.setTextSize(20);
        date.setTextColor(0xB3FFFFFF);
        date.setIncludeFontPadding(false);
        date.setSingleLine(true);
        addHeader(date, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                margin, dp(176));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Inputs", "Wi-Fi", "Settings"};
        for (int i = 0; i < 3; i++) {
            TextView p = tv(16, fReg, Color.WHITE);
            p.setGravity(Gravity.CENTER);
            p.setPadding(dp(22), 0, dp(22), 0);
            p.setText(names[i]);
            p.setMaxWidth(dp(300));
            p.setEllipsize(TextUtils.TruncateAt.END);
            pills[i] = p;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            if (i > 0) lp.leftMargin = dp(12);
            bar.addView(p, lp);
            stylePill(i, false);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.TOP | Gravity.END;
        blp.topMargin = dp(36);
        blp.rightMargin = margin;
        header.addView(bar, blp);
    }

    void addHeader(View v, int w, int h, int x, int y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        header.addView(v, lp);
    }

    void stylePill(int i, boolean focus) {
        TextView p = pills[i];
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(focus ? 0xE6FFFFFF : 0x33FFFFFF);
        p.setBackground(g);
        p.setTextColor(focus ? 0xFF000000 : Color.WHITE);
        float s = focus ? 1.06f : 1f;
        p.animate().scaleX(s).scaleY(s).setDuration(160).start();
    }

    void buildDropdown() {
        dropdown = new LinearLayout(this);
        dropdown.setOrientation(LinearLayout.VERTICAL);
        dropdown.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(0xF0242426);
        g.setStroke(dp(1), 0x40FFFFFF);
        dropdown.setBackground(g);
        dropdown.setVisibility(View.GONE);
        root.addView(dropdown, new FrameLayout.LayoutParams(dp(260), FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        updateWifi();
        loadAppsAsync();
    }

    @Override
    protected void onPause() {
        saveFocus();
        super.onPause();
    }

    @Override
    protected void onStop() {
        closeDropdown();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while we are already on screen: close menus, jump back to the dock.
        closeDropdown();
        if (built && zone != Z_DOCK && !dock.isEmpty()) {
            zone = Z_DOCK;
            applyFocus(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (dropdownOpen) closeDropdown();
        // otherwise do nothing: this is the home screen
    }

    void askLocationOnce() {
        // Android only shows the Wi-Fi network name if location permission is granted.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("askedLocation", false)) {
            prefs.edit().putBoolean("askedLocation", true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updateWifi();
    }

    void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening");
    }

    void updateWifi() {
        String text = "Wi-Fi";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) {
                text = "Offline";
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                String s = wi == null ? null : wi.getSSID();
                if (s != null && !s.contains("unknown ssid") && !s.equals("0x")) {
                    text = s.replace("\"", "");
                }
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = "Ethernet";
            }
        } catch (Exception ignored) {
        }
        pills[1].setText(text);
    }

    // ------------------------------------------------------------ apps

    void loadAppsAsync() {
        if (loading) return;
        loading = true;
        final String old = sig0;
        new Thread(() -> {
            final String[] sigOut = new String[1];
            List<App> res = null;
            try {
                res = queryApps(old, sigOut);
            } catch (Exception ignored) {
            }
            final List<App> fres = res;
            runOnUiThread(() -> {
                loading = false;
                if (fres != null) setApps(fres, sigOut[0]);
            });
        }).start();
    }

    /** Returns null when nothing changed since the last load. */
    List<App> queryApps(String oldSig, String[] sigOut) {
        PackageManager pm = getPackageManager();
        Intent in = new Intent(Intent.ACTION_MAIN);
        in.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(in, 0);
        List<ResolveInfo> use = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || !seen.add(p)) continue;
            use.add(ri);
        }
        List<String> pk = new ArrayList<>(seen);
        Collections.sort(pk);
        sigOut[0] = TextUtils.join(",", pk);
        if (sigOut[0].equals(oldSig)) return null;

        List<App> list = new ArrayList<>();
        for (ResolveInfo ri : use) {
            App a = new App();
            a.pkg = ri.activityInfo.packageName;
            a.cn = new ComponentName(a.pkg, ri.activityInfo.name);
            a.label = String.valueOf(ri.loadLabel(pm));
            Drawable art = ri.activityInfo.loadBanner(pm);
            if (art != null) {
                a.isBanner = true;
            } else {
                art = ri.loadIcon(pm);
            }
            a.art = art;
            list.add(a);
        }
        Collections.sort(list, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return list;
    }

    void setApps(List<App> list, String sig) {
        if (built) focusPkg = pkgOfCurrent();
        all = list;
        sig0 = sig;
        dock = resolveDock();
        rebuild();
        built = true;
    }

    List<App> resolveDock() {
        List<App> d = new ArrayList<>();
        String saved = prefs.getString("dock", null);
        if (saved != null) {
            if (!saved.isEmpty()) {
                for (String p : saved.split("\\|")) {
                    App a = byPkg(p);
                    if (a != null && !d.contains(a)) d.add(a);
                }
            }
            return d;
        }
        for (String p : DEFAULT_DOCK) {
            App a = byPkg(p);
            if (a != null && !d.contains(a)) d.add(a);
        }
        for (App a : all) {
            if (d.size() >= MIN_COLS) break;
            if (!d.contains(a)) d.add(a);
        }
        return d;
    }

    App byPkg(String p) {
        for (App a : all) if (a.pkg.equals(p)) return a;
        return null;
    }

    int indexOf(List<App> l, String pkg) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    void saveDock() {
        StringBuilder sb = new StringBuilder();
        for (App a : dock) {
            if (sb.length() > 0) sb.append('|');
            sb.append(a.pkg);
        }
        prefs.edit().putString("dock", sb.toString()).apply();
    }

    String pkgOfCurrent() {
        App a = currentApp();
        return a == null ? null : a.pkg;
    }

    void saveFocus() {
        String p = pkgOfCurrent();
        if (p != null) prefs.edit().putString("lastPkg", p).apply();
    }

    App currentApp() {
        if (zone == Z_DOCK && dockIdx >= 0 && dockIdx < dock.size()) return dock.get(dockIdx);
        if (zone == Z_GRID && gridIdx >= 0 && gridIdx < grid.size()) return grid.get(gridIdx);
        return null;
    }

    // ------------------------------------------------------------ layout

    int tileX(int col) {
        return margin + col * (tileW + gap);
    }

    int scrollForRow(int row) {
        return row0Top + row * rowH - dp(76);
    }

    void addToContent(View v, int w, int h, float x, float y) {
        content.addView(v, new FrameLayout.LayoutParams(w, h));
        v.setTranslationX(x);
        v.setTranslationY(y);
    }

    void rebuild() {
        content.removeAllViews();
        dockTiles.clear();
        gridTiles.clear();
        focusedTile = null;
        highlightShown = false;

        grid = new ArrayList<>();
        for (App a : all) if (!dock.contains(a)) grid.add(a);

        cols = Math.max(MIN_COLS, Math.min(MAX_DOCK, dock.size()));
        int avail = W - 2 * margin;
        tileW = (avail - (cols - 1) * gap) / cols;
        tileH = tileW * 9 / 16;
        int bandH = tileH + dp(76);
        bandTop = H - dp(36) - bandH;
        dockY = bandTop + dp(20);
        row0Top = H + dp(40);
        rowH = tileH + dp(64);
        int rows = (grid.size() + cols - 1) / cols;
        int contentH = rows > 0 ? scrollForRow(rows - 1) + H : H;
        content.setLayoutParams(new FrameLayout.LayoutParams(W, contentH));

        band = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(30));
        bg.setColor(0xFF18181A);
        band.setBackground(bg);
        addToContent(band, W - 2 * margin + dp(40), bandH, margin - dp(20), bandTop);

        glowView = new View(this);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(320));
        glowDrawable.setColors(new int[]{0, 0});
        glowView.setBackground(glowDrawable);
        addToContent(glowView, dp(640), dp(640), margin + dp(160) - dp(320), bandTop - dp(44) - dp(320));

        bigName = tv(44, fLight, Color.WHITE);
        bigName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        addToContent(bigName, W - 2 * margin, dp(64), margin, bandTop - dp(76));

        float r = dp(12);
        for (int i = 0; i < dock.size(); i++) {
            TileView t = new TileView(this, dock.get(i), r);
            dockTiles.add(t);
            addToContent(t, tileW, tileH, tileX(i), dockY);
        }
        for (int i = 0; i < grid.size(); i++) {
            TileView t = new TileView(this, grid.get(i), r);
            gridTiles.add(t);
            addToContent(t, tileW, tileH, tileX(i % cols), row0Top + (i / cols) * rowH);
        }

        highlight = new View(this);
        GradientDrawable hl = new GradientDrawable();
        hl.setCornerRadius(dp(16));
        hl.setColor(0x2EFFFFFF);
        hl.setStroke(dp(2), 0x99FFFFFF);
        highlight.setBackground(hl);
        highlight.setAlpha(0f);
        addToContent(highlight, Math.round(tileW * FOCUS_SCALE) + dp(14),
                Math.round(tileH * FOCUS_SCALE) + dp(14), 0, 0);

        tileLabel = tv(17, fReg, Color.WHITE);
        tileLabel.setGravity(Gravity.CENTER);
        tileLabel.setAlpha(0f);
        addToContent(tileLabel, tileW * 2, dp(26), 0, 0);

        if (focusPkg != null) {
            int di = indexOf(dock, focusPkg);
            if (di >= 0) {
                zone = Z_DOCK;
                dockIdx = di;
            } else {
                int gi = indexOf(grid, focusPkg);
                if (gi >= 0) {
                    zone = Z_GRID;
                    gridIdx = gi;
                }
            }
            focusPkg = null;
        }
        if (dockIdx >= dock.size()) dockIdx = Math.max(0, dock.size() - 1);
        if (gridIdx >= grid.size()) gridIdx = Math.max(0, grid.size() - 1);
        if (zone == Z_DOCK && dock.isEmpty()) zone = grid.isEmpty() ? Z_TOP : Z_GRID;
        if (zone == Z_GRID && grid.isEmpty()) zone = dock.isEmpty() ? Z_TOP : Z_DOCK;

        curScroll = 0;
        content.setTranslationY(0);
        header.setAlpha(1f);
        applyFocus(false);
    }

    // ------------------------------------------------------------ focus drawing

    void scrollTo(int target, boolean animate) {
        if (target == curScroll && !animate) {
            content.setTranslationY(-target);
            return;
        }
        if (target != curScroll || !animate) {
            if (animate) {
                content.animate().translationY(-target).setDuration(320)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            } else {
                content.animate().cancel();
                content.setTranslationY(-target);
            }
        }
        float a = target > 0 ? 0f : 1f;
        if (animate) header.animate().alpha(a).setDuration(220).start();
        else header.setAlpha(a);
        curScroll = target;
    }

    void applyFocus(boolean animate) {
        if (focusedTile != null) {
            focusedTile.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            focusedTile = null;
        }
        for (int i = 0; i < 3; i++) stylePill(i, zone == Z_TOP && i == pillIdx);
        if (zone != Z_TOP) closeDropdown();

        updateDockInfo(animate);

        if (zone == Z_TOP) {
            highlight.animate().alpha(0f).setDuration(150).start();
            tileLabel.animate().alpha(0f).setDuration(150).start();
            highlightShown = false;
            scrollTo(0, animate);
            return;
        }

        TileView t;
        int tx, ty;
        if (zone == Z_DOCK) {
            if (dockIdx >= dockTiles.size()) return;
            t = dockTiles.get(dockIdx);
            tx = tileX(dockIdx);
            ty = dockY;
        } else {
            if (gridIdx >= gridTiles.size()) return;
            t = gridTiles.get(gridIdx);
            tx = tileX(gridIdx % cols);
            ty = row0Top + (gridIdx / cols) * rowH;
        }
        focusedTile = t;
        t.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setDuration(200).start();

        scrollTo(zone == Z_GRID ? scrollForRow(gridIdx / cols) : 0, animate);

        float hx = tx + tileW / 2f - highlight.getLayoutParams().width / 2f;
        float hy = ty + tileH / 2f - highlight.getLayoutParams().height / 2f;
        if (animate && highlightShown) {
            highlight.animate().translationX(hx).translationY(hy).alpha(1f).setDuration(320)
                    .setInterpolator(new OvershootInterpolator(1.1f)).start();
        } else {
            highlight.animate().cancel();
            highlight.setTranslationX(hx);
            highlight.setTranslationY(hy);
            if (animate) highlight.animate().alpha(1f).setDuration(150).start();
            else highlight.setAlpha(1f);
        }
        highlightShown = true;

        App a = t.app;
        tileLabel.setText(a.label);
        tileLabel.setTranslationX(tx + tileW / 2f - tileW);
        tileLabel.setTranslationY(ty + tileH + dp(16));
        tileLabel.animate().alpha(1f).setDuration(150).start();
    }

    void updateDockInfo(boolean animate) {
        if (dock.isEmpty()) {
            bigName.setText("");
            return;
        }
        App a = dock.get(Math.min(dockIdx, dock.size() - 1));
        bigName.setText(a.label);
        int c = glowOf(a);
        if (!animate || glowColor == 0) {
            setGlow(c);
        } else if (c != glowColor) {
            ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, c);
            va.setDuration(400);
            va.addUpdateListener(an -> setGlow((Integer) an.getAnimatedValue()));
            va.start();
        }
    }

    void setGlow(int c) {
        glowColor = c;
        int rgb = c & 0x00FFFFFF;
        glowDrawable.setColors(new int[]{rgb | (110 << 24), rgb});
    }

    /** Picks the most vivid colour from the tile art (done once per app, on a 16x9 copy). */
    int glowOf(App a) {
        if (a.glowDone) return a.glow;
        a.glowDone = true;
        int res = 0xFFB0B0B8;
        try {
            if (a.art != null) {
                Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                Rect old = a.art.copyBounds();
                a.art.setBounds(0, 0, 16, 9);
                a.art.draw(c);
                a.art.setBounds(old);
                float[] hsv = new float[3];
                float best = -1f;
                int bestPx = 0;
                for (int y = 0; y < 9; y++) {
                    for (int x = 0; x < 16; x++) {
                        int p = b.getPixel(x, y);
                        if (Color.alpha(p) < 128) continue;
                        Color.colorToHSV(p, hsv);
                        float score = hsv[1] * hsv[2];
                        if (score > best) {
                            best = score;
                            bestPx = p;
                        }
                    }
                }
                b.recycle();
                if (best > 0.08f) {
                    Color.colorToHSV(bestPx, hsv);
                    hsv[1] = Math.min(1f, hsv[1] * 1.1f);
                    hsv[2] = Math.max(0.6f, hsv[2]);
                    res = Color.HSVToColor(hsv);
                }
            }
        } catch (Exception ignored) {
        }
        a.glow = res;
        return res;
    }

    // ------------------------------------------------------------ keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) {
                    longHandled = false;
                    e.startTracking();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (dropdownOpen) moveDropdown(-1);
                else moveUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (dropdownOpen) moveDropdown(1);
                else moveDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!dropdownOpen) moveSide(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!dropdownOpen) moveSide(1);
                return true;
            default:
                return super.onKeyDown(keyCode, e);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            longHandled = true;
            if (!dropdownOpen && zone != Z_TOP) showMenu();
            return true;
        }
        return super.onKeyLongPress(keyCode, e);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (longHandled) {
                longHandled = false;
                return true;
            }
            if (e.isCanceled()) return true;
            if (dropdownOpen) selectDropdown();
            else activate();
            return true;
        }
        return super.onKeyUp(keyCode, e);
    }

    void moveUp() {
        if (!built) return;
        switch (zone) {
            case Z_GRID:
                if (gridIdx / cols > 0) {
                    gridIdx -= cols;
                } else if (!dock.isEmpty()) {
                    zone = Z_DOCK;
                    dockIdx = Math.min(gridIdx % cols, dock.size() - 1);
                } else {
                    zone = Z_TOP;
                }
                break;
            case Z_DOCK:
                zone = Z_TOP;
                break;
            default:
                return;
        }
        applyFocus(true);
    }

    void moveDown() {
        if (!built) return;
        switch (zone) {
            case Z_TOP:
                if (!dock.isEmpty()) zone = Z_DOCK;
                else if (!grid.isEmpty()) zone = Z_GRID;
                else return;
                break;
            case Z_DOCK:
                if (grid.isEmpty()) return;
                zone = Z_GRID;
                gridIdx = Math.min(dockIdx, grid.size() - 1);
                break;
            default:
                int next = gridIdx + cols;
                if (next < grid.size()) gridIdx = next;
                else if (gridIdx / cols < (grid.size() - 1) / cols) gridIdx = grid.size() - 1;
                else return;
        }
        applyFocus(true);
    }

    void moveSide(int d) {
        if (!built) return;
        if (zone == Z_TOP) {
            int n = Math.max(0, Math.min(2, pillIdx + d));
            if (n == pillIdx) return;
            pillIdx = n;
        } else if (zone == Z_DOCK) {
            int n = Math.max(0, Math.min(dock.size() - 1, dockIdx + d));
            if (n == dockIdx) return;
            dockIdx = n;
        } else {
            int col = gridIdx % cols;
            int n = gridIdx + d;
            if (d < 0 && col == 0) return;
            if (d > 0 && (col == cols - 1 || n >= grid.size())) return;
            gridIdx = n;
        }
        applyFocus(true);
    }

    void activate() {
        if (!built) return;
        if (zone == Z_TOP) {
            if (pillIdx == 0) {
                openDropdown();
            } else if (pillIdx == 1) {
                startSafe(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } else {
                startSafe(new Intent(Settings.ACTION_SETTINGS));
            }
            return;
        }
        App a = currentApp();
        if (a != null) launch(a);
    }

    void launch(App a) {
        saveFocus();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(a.cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafe(i);
    }

    void startSafe(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ inputs dropdown

    void openDropdown() {
        ddIds.clear();
        ddRows.clear();
        dropdown.removeAllViews();
        List<String[]> found = new ArrayList<>();
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo info : m.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence cl = info.loadCustomLabel(this);
                CharSequence l = (cl != null && cl.length() > 0) ? cl : info.loadLabel(this);
                found.add(new String[]{String.valueOf(l), info.getId()});
            }
        } catch (Exception ignored) {
        }
        Collections.sort(found, new Comparator<String[]>() {
            @Override
            public int compare(String[] x, String[] y) {
                return x[0].compareToIgnoreCase(y[0]);
            }
        });
        found.add(new String[]{"Live TV", null});
        for (String[] f : found) {
            TextView r = tv(18, fReg, Color.WHITE);
            r.setText(f[0]);
            r.setPadding(dp(18), dp(11), dp(18), dp(11));
            dropdown.addView(r, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ddRows.add(r);
            ddIds.add(f[1]);
        }
        int[] loc = new int[2];
        pills[0].getLocationInWindow(loc);
        dropdown.setTranslationX(loc[0]);
        dropdown.setTranslationY(loc[1] + pills[0].getHeight() + dp(10));
        dropdown.setVisibility(View.VISIBLE);
        dropdownOpen = true;
        ddIdx = 0;
        styleDropdown();
    }

    void closeDropdown() {
        dropdownOpen = false;
        dropdown.setVisibility(View.GONE);
    }

    void moveDropdown(int d) {
        ddIdx = Math.max(0, Math.min(ddRows.size() - 1, ddIdx + d));
        styleDropdown();
    }

    void styleDropdown() {
        for (int i = 0; i < ddRows.size(); i++) {
            if (i == ddIdx) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(12));
                g.setColor(0x55FFFFFF);
                ddRows.get(i).setBackground(g);
            } else {
                ddRows.get(i).setBackground(null);
            }
        }
    }

    void selectDropdown() {
        if (ddIdx < 0 || ddIdx >= ddIds.size()) return;
        String id = ddIds.get(ddIdx);
        closeDropdown();
        if (id == null) {
            startSafe(new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI));
        } else {
            startSafe(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)));
        }
    }

    // ------------------------------------------------------------ edit menu (hold OK)

    void showMenu() {
        final App a = currentApp();
        if (a == null) return;
        final boolean inDock = zone == Z_DOCK;
        String[] items;
        if (inDock) {
            items = new String[]{"Move left", "Move right", "Remove from dock"};
        } else if (dock.size() < MAX_DOCK) {
            items = new String[]{"Add to dock"};
        } else {
            items = new String[]{"Dock is full (7 apps). Remove one first."};
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(items, (d, which) -> {
                    if (inDock) {
                        int i = dock.indexOf(a);
                        if (which == 0 && i > 0) {
                            Collections.swap(dock, i, i - 1);
                        } else if (which == 1 && i < dock.size() - 1) {
                            Collections.swap(dock, i, i + 1);
                        } else if (which == 2) {
                            dock.remove(a);
                        }
                    } else if (dock.size() < MAX_DOCK) {
                        dock.add(a);
                    } else {
                        return;
                    }
                    saveDock();
                    focusPkg = a.pkg;
                    rebuild();
                })
                .show();
    }
}
