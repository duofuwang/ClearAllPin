package com.duofu.clearall;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** 样式设置界面，改动即时通知桌面应用。 */
public class SettingsActivity extends Activity {

    static final Uri CONFIG_URI = Uri.parse(ConfigProvider.CONFIG_URI);

    private SharedPreferences prefs;
    private boolean settingUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("config", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, dp(44), pad, pad);
        root.setBackgroundColor(0xFF0F1114);

        TextView title = new TextView(this);
        title.setText("清除按钮样式");
        title.setTextColor(0xFFF2F2F2);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("改动即时生效，打开最近任务即可查看效果。");
        sub.setTextColor(0xFF9AA0A6);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        addSectionCustom(root, "高亮形状", ConfigProvider.KEY_RADIUS,
                new String[]{"小圆角矩形", "圆形药丸", "直角"},
                new int[]{8, 24, 0}, ConfigProvider.DEF_RADIUS, 100);
        addSectionCustom(root, "左右留白", ConfigProvider.KEY_PAD,
                new String[]{"默认（12dp）", "无（0dp）", "宽（20dp）"},
                new int[]{12, 0, 20}, ConfigProvider.DEF_PAD, 200);
        addSection(root, "图标", ConfigProvider.KEY_ICON,
                new String[]{"显示图标", "不显示图标"},
                new int[]{1, 0}, ConfigProvider.DEF_ICON);

        setContentView(root);
    }

    /** 预设单选 + 「自定义」数值输入。 */
    private void addSectionCustom(LinearLayout root, String titleText, final String key,
            String[] labels, int[] values, int def, final int max) {
        root.addView(sectionTitle(titleText));
        final RadioGroup group = new RadioGroup(this);

        int current = prefs.getInt(key, def);
        boolean matched = false;
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = presetRadio(labels[i], values[i]);
            group.addView(rb);
            if (values[i] == current) {
                rb.setChecked(true);
                matched = true;
            }
        }
        final int customId = View.generateViewId();
        final RadioButton custom = presetRadio("自定义", Integer.MIN_VALUE);
        custom.setId(customId);
        group.addView(custom);
        if (!matched) {
            custom.setChecked(true);
        }
        root.addView(group);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("自定义数值（dp），输入即生效");
        input.setTextColor(0xFFE8EAED);
        input.setHintTextColor(0xFF6F7378);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = dp(38);
        ilp.topMargin = dp(2);
        ilp.bottomMargin = dp(6);
        root.addView(input, ilp);
        if (!matched) {
            input.setText(String.valueOf(current));
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                if (settingUp) {
                    return;
                }
                if (id == customId) {
                    Integer v = parse(input);
                    if (v == null) {
                        // 输入框空时回填当前值，避免选了自定义却没生效
                        v = prefs.getInt(key, def);
                        settingUp = true;
                        input.setText(String.valueOf(v));
                        settingUp = false;
                    }
                    save(key, clamp(v, max));
                } else {
                    View v = g.findViewById(id);
                    if (v != null && v.getTag() instanceof Integer) {
                        save(key, (Integer) v.getTag());
                    }
                }
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (settingUp) {
                    return;
                }
                Integer v = parse(input);
                if (v == null) {
                    return;
                }
                if (!custom.isChecked()) {
                    settingUp = true;
                    custom.setChecked(true);
                    settingUp = false;
                }
                save(key, clamp(v, max));
            }
        });
    }

    /** 仅预设单选。 */
    private void addSection(LinearLayout root, String titleText, final String key,
            String[] labels, final int[] values, int def) {
        root.addView(sectionTitle(titleText));
        final RadioGroup group = new RadioGroup(this);
        int current = prefs.getInt(key, def);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = presetRadio(labels[i], values[i]);
            group.addView(rb);
            if (values[i] == current) {
                rb.setChecked(true);
            }
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                View v = g.findViewById(id);
                if (v != null && v.getTag() instanceof Integer) {
                    save(key, (Integer) v.getTag());
                }
            }
        });
        root.addView(group);
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF8AB4F8);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private RadioButton presetRadio(String label, int value) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextColor(0xFFE8EAED);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rb.setId(View.generateViewId());
        rb.setTag(value);
        return rb;
    }

    private void save(String key, int v) {
        prefs.edit().putInt(key, v).apply();
        getContentResolver().notifyChange(CONFIG_URI, null);
    }

    private static Integer parse(EditText e) {
        try {
            String s = e.getText().toString().trim();
            if (s.isEmpty()) {
                return null;
            }
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int clamp(int v, int max) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, max);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}
