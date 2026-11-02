package com.angel.tvweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * WebView a pantalla completa para Google TV / Android TV.
 * Control remoto: flechas = mover el cursor, OK = clic, Atrás = volver / salir de pantalla completa.
 * Si el cursor está en el borde superior/inferior, otra flecha desplaza la página.
 */
public class MainActivity extends Activity {

    // Cambia aquí la página que quieres mostrar
    private static final String HOME_URL = "https://animeav1.com/";

    // User agent de escritorio para que el sitio use su diseño ancho (mejor en TV)
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT;

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private float cx, cy;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        cursor = new CursorView(this);
        int size = dp(36);
        root.addView(cursor, new FrameLayout.LayoutParams(size, size));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(USER_AGENT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);          // para poder bloquear ventanas emergentes
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String scheme = r.getUrl().getScheme();
                // Solo http/https dentro de la app; bloquea intent://, market://, etc.
                return !("http".equals(scheme) || "https".equals(scheme));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, Message msg) {
                return false; // ignora ventanas emergentes (publicidad)
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCallback = cb;
                root.addView(view, 0, new FrameLayout.LayoutParams(MATCH, MATCH));
                web.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onHideCustomView() { hideCustom(); }
        });

        root.post(() -> {
            cx = root.getWidth() / 2f;
            cy = root.getHeight() / 2f;
            placeCursor();
        });

        web.loadUrl(HOME_URL);
    }

    // ---------- Cursor ----------
    private void placeCursor() {
        cursor.setX(cx - cursor.getWidth() / 2f);
        cursor.setY(cy - cursor.getHeight() / 2f);
    }

    private void move(int key, int repeat) {
        float step = dp(10) + Math.min(repeat, 25) * dp(1) * 1.2f;
        int w = root.getWidth(), h = root.getHeight();
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (cy <= 1) scrollPage(-1); else cy = Math.max(0, cy - step);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (cy >= h - 1) scrollPage(1); else cy = Math.min(h - 1, cy + step);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cx = Math.max(0, cx - step);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cx = Math.min(w - 1, cx + step);
                break;
        }
        placeCursor();
    }

    private void scrollPage(int dir) {
        if (customView == null) web.scrollBy(0, dir * dp(140));
    }

    // Convierte OK en un toque real en la posición del cursor (funciona también dentro de iframes)
    private void tap() {
        final View target = customView != null ? customView : web;
        final long t = SystemClock.uptimeMillis();
        int[] a = new int[2], b = new int[2];
        target.getLocationOnScreen(a);
        root.getLocationOnScreen(b);
        final float x = cx + b[0] - a[0], y = cy + b[1] - a[1];

        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        down.recycle();

        handler.postDelayed(() -> {
            MotionEvent up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            target.dispatchTouchEvent(up);
            up.recycle();
        }, 70);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(e.getKeyCode(), e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) tap();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // ---------- Pantalla completa de video / Atrás ----------
    private void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) hideCustom();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- Vista del cursor ----------
    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c) {
            super(c);
            fill.setColor(Color.argb(200, 255, 255, 255));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4f);
            stroke.setColor(Color.parseColor("#1c2f3e"));
            dot.setColor(Color.parseColor("#2389cc"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float c = getWidth() / 2f, r = c - 4f;
            canvas.drawCircle(c, c, r, fill);
            canvas.drawCircle(c, c, r, stroke);
            canvas.drawCircle(c, c, r / 3f, dot);
        }
    }
}
