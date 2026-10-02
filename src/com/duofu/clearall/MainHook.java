package com.duofu.clearall;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 在最近任务底部操作排里插一个常驻的「✕ 全部清除」按钮。
 *  按钮是操作排的真实子项，外观抄旁边「屏幕截图」，点击反射调 RecentsView#dismissAllTasks。 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "ClearAllPin: ";
    private static final String PKG = "com.android.launcher3";
    private static final String CLS_RECENTS = "com.android.quickstep.views.RecentsView";
    private static final String CLS_ACTIONS = "com.android.quickstep.views.OverviewActionsView";

    static final Uri CONFIG_URI = Uri.parse(ConfigProvider.CONFIG_URI);

    private Object recentsView;
    private Method dismissAllTasks;
    private TextView myButton;
    private TextView mStock;
    private Drawable[] mStockIcons;
    private int cfgRadius = ConfigProvider.DEF_RADIUS;
    private int cfgPad = ConfigProvider.DEF_PAD;
    private int cfgIcon = ConfigProvider.DEF_ICON;
    private static boolean sObserverRegistered;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) {
            return;
        }
        try {
            Class<?> recentsCls = XposedHelpers.findClass(CLS_RECENTS, lpparam.classLoader);
            Class<?> actionsCls = XposedHelpers.findClass(CLS_ACTIONS, lpparam.classLoader);

            // 单独 try：拿不到只影响点击，不能连累按钮安装
            try {
                dismissAllTasks = recentsCls.getDeclaredMethod("dismissAllTasks", View.class);
                dismissAllTasks.setAccessible(true);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "dismissAllTasks resolve failed: " + t);
            }

            XposedBridge.hookAllConstructors(recentsCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    recentsView = param.thisObject;
                }
            });

            XposedHelpers.findAndHookMethod(actionsCls, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    install((ViewGroup) param.thisObject);
                }
            });

            XposedBridge.log(TAG + "hooks installed v6");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "init failed: " + t);
        }
    }

    private void install(ViewGroup actionsRoot) {
        try {
            Context ctx = actionsRoot.getContext();
            int rowId = ctx.getResources().getIdentifier("action_buttons", "id", PKG);
            if (rowId == 0) {
                XposedBridge.log(TAG + "action_buttons id not found");
                return;
            }
            View rowV = actionsRoot.findViewById(rowId);
            if (!(rowV instanceof ViewGroup)) {
                XposedBridge.log(TAG + "action_buttons not a ViewGroup");
                return;
            }
            ViewGroup row = (ViewGroup) rowV;
            if (myButton != null && myButton.getParent() == row) {
                return;
            }

            TextView button = new TextView(ctx);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setVisibility(View.VISIBLE);
            button.setClickable(true);
            button.setFocusable(true);
            button.setText(label(ctx));

            TextView stock = findStock(ctx, row);
            mStock = stock;
            if (stock != null) {
                copyLook(stock, button);
                mStockIcons = stock.getCompoundDrawablesRelative();
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginStart(nativeGap(row));
            row.addView(button, lp);

            // padding 会在 init 阶段被系统改写成 0，等落定后再统一对齐。
            if (stock != null) {
                ViewGroup.LayoutParams slp = stock.getLayoutParams();
                if (slp != null && slp.height > 0) {
                    lp.height = slp.height;
                    button.setLayoutParams(lp);
                    XposedBridge.log(TAG + "height from lp: " + slp.height);
                }
                matchHeightLater(actionsRoot, stock, button);
            }

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Object rv = recentsView;
                    if (rv == null || dismissAllTasks == null) {
                        XposedBridge.log(TAG + "click: recents not captured");
                        return;
                    }
                    try {
                        dismissAllTasks.invoke(rv, v);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "dismiss failed: " + t);
                    }
                }
            });

            myButton = button;
            readConfig(ctx);
            applyStyle();
            observeConfig(ctx);
            XposedBridge.log(TAG + "button added to row, stock=" + stock);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "install failed: " + t);
        }
    }

    private static TextView findStock(Context ctx, ViewGroup row) {
        try {
            int id = ctx.getResources().getIdentifier("action_screenshot", "id", PKG);
            if (id != 0) {
                View v = row.findViewById(id);
                if (v instanceof TextView) {
                    return (TextView) v;
                }
            }
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof TextView) {
                return (TextView) c;
            }
        }
        return null;
    }

    /** 复制邻居的文字样式与度量；背景、图标由 applyStyle() 按配置处理。 */
    private static void copyLook(TextView stock, TextView mine) {
        mine.setTextColor(stock.getTextColors());
        mine.setTextSize(TypedValue.COMPLEX_UNIT_PX, stock.getTextSize());
        mine.setTypeface(stock.getTypeface());
        mine.setGravity(stock.getGravity());
        mine.setPadding(stock.getPaddingLeft(), stock.getPaddingTop(),
                stock.getPaddingRight(), stock.getPaddingBottom());
        mine.setMinWidth(stock.getMinWidth());
        mine.setMinHeight(stock.getMinHeight());
        mine.setElevation(stock.getElevation());
        mine.setStateListAnimator(null);
        // 背景/图标不在这里处理：由 applyStyle() 按当前用户配置统一应用。
    }

    /** 读取样式配置，读不到时沿用默认值。 */
    private void readConfig(Context ctx) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(CONFIG_URI, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                cfgRadius = c.getInt(c.getColumnIndex(ConfigProvider.KEY_RADIUS));
                cfgPad = c.getInt(c.getColumnIndex(ConfigProvider.KEY_PAD));
                cfgIcon = c.getInt(c.getColumnIndex(ConfigProvider.KEY_ICON));
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** 把当前配置应用到两个按钮。 */
    private void applyStyle() {
        try {
            TextView mine = myButton;
            if (mine == null) {
                return;
            }
            Context ctx = mine.getContext();
            applySoftRipple(ctx, mine, cfgRadius);
            mine.setPadding(dp(ctx, cfgPad), mine.getPaddingTop(),
                    dp(ctx, cfgPad), mine.getPaddingBottom());
            applyIconButtons(mStock, mine, cfgIcon == 1);
            TextView stock = mStock;
            if (stock != null) {
                applySoftRipple(ctx, stock, cfgRadius);
                stock.setPadding(dp(ctx, cfgPad), stock.getPaddingTop(),
                        dp(ctx, cfgPad), stock.getPaddingBottom());
            }
            XposedBridge.log(TAG + "style applied: r=" + cfgRadius
                    + " pad=" + cfgPad + " icon=" + cfgIcon);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "applyStyle error: " + t);
        }
    }

    /** 监听设置改动，实时重新应用。 */
    private void observeConfig(final Context ctx) {
        if (sObserverRegistered) {
            return;
        }
        sObserverRegistered = true;
        try {
            ctx.getContentResolver().registerContentObserver(CONFIG_URI, false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            readConfig(ctx);
                            applyStyle();
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "observe error: " + t);
        }
    }

    /** 图标开关作用于两个按钮：关掉整排无图标。 */
    private void applyIconButtons(TextView stock, TextView mine, boolean on) {
        if (!on) {
            mine.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
            if (stock != null) {
                stock.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
            }
            return;
        }
        if (stock != null && mStockIcons != null && mStockIcons.length >= 4) {
            stock.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    mStockIcons[0], mStockIcons[1], mStockIcons[2], mStockIcons[3]);
            addCrossIcon(stock, mine);
        }
    }

    /** 自建水波纹背景。颜色必须从外层容器解析：
     *  个别子视图 context 的主题里 colorControlHighlight 是不透明纯白，全不透明时强制换半透明。 */
    private static void applySoftRipple(Context themeCtx, TextView v, int radiusDp) {
        int highlight = attrColor(themeCtx, android.R.attr.colorControlHighlight, 0x33FFFFFF);
        if ((highlight >>> 24) == 0xFF) {
            highlight = 0x33FFFFFF;
        }
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(dp(v.getContext(), radiusDp));
        mask.setColor(Color.WHITE);
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(highlight), null, mask));
        v.setBackgroundTintList(null);
        v.setForeground(null);
        XposedBridge.log(TAG + "ripple on id=0x" + Integer.toHexString(v.getId())
                + " color=0x" + Integer.toHexString(highlight));
    }

    /** 等邻居落定后对齐两个按钮的内边距与高度。 */
    private void matchHeightLater(final View host, final TextView stock, final TextView mine) {
        host.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    if (stock.getHeight() > 0 && mine.getHeight() > 0) {
                        int hPad = dp(mine.getContext(), cfgPad);
                        mine.setPadding(hPad, mine.getPaddingTop(), hPad, mine.getPaddingBottom());
                        stock.setPadding(hPad, stock.getPaddingTop(), hPad, stock.getPaddingBottom());
                        ViewGroup.LayoutParams lp = mine.getLayoutParams();
                        if (lp != null && lp.height != stock.getHeight()) {
                            lp.height = stock.getHeight();
                            mine.setLayoutParams(lp);
                        }
                        host.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
        });
    }

    /** 图标尺寸与间距照抄邻居；X 自绘，颜色跟随文字。 */
    private static void addCrossIcon(TextView sibling, TextView mine) {
        int size = 0;
        int pad = sibling.getCompoundDrawablePadding();
        for (Drawable d : sibling.getCompoundDrawablesRelative()) {
            if (d != null && d.getIntrinsicWidth() > 0) {
                size = d.getIntrinsicWidth();
                break;
            }
        }
        if (size <= 0) {
            size = dp(sibling.getContext(), 20);
        }
        if (pad <= 0) {
            pad = dp(sibling.getContext(), 8);
        }
        Drawable icon = new CrossDrawable(mine, size, Math.max(2f, size * 0.09f));
        mine.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        mine.setCompoundDrawablePadding(pad);
    }

    /** 自绘的 X：绘制时实时取宿主文字颜色，状态变化自动同步。 */
    private static final class CrossDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private final TextView owner;

        CrossDrawable(TextView owner, int size, float stroke) {
            this.owner = owner;
            this.size = size;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas canvas) {
            paint.setColor(owner != null ? owner.getCurrentTextColor() : 0xFFEEEEEE);
            Rect b = getBounds();
            float in = b.width() * 0.27f;
            canvas.drawLine(b.left + in, b.top + in, b.right - in, b.bottom - in, paint);
            canvas.drawLine(b.right - in, b.top + in, b.left + in, b.bottom - in, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** 沿用那一排自己的按钮间距。 */
    private static int nativeGap(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            ViewGroup.LayoutParams lp = row.getChildAt(i).getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                int m = ((ViewGroup.MarginLayoutParams) lp).getMarginStart();
                if (m > 0) {
                    return m;
                }
            }
        }
        return dp(row.getContext(), 8);
    }

    private static CharSequence label(Context ctx) {
        int id = ctx.getResources().getIdentifier("recents_clear_all", "string", PKG);
        return id != 0 ? ctx.getString(id) : "全部清除";
    }

    private static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                ctx.getResources().getDisplayMetrics());
    }

    private static int attrColor(Context ctx, int attr, int fallback) {
        TypedArray a = null;
        try {
            a = ctx.obtainStyledAttributes(new int[]{attr});
            return a.getColor(0, fallback);
        } catch (Throwable ignored) {
            return fallback;
        } finally {
            if (a != null) {
                a.recycle();
            }
        }
    }
}
