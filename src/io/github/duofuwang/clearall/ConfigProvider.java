package io.github.duofuwang.clearall;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** 样式配置的只读通道；键名与默认值集中在这里。 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "io.github.duofuwang.clearall.config";
    public static final String CONFIG_URI = "content://" + AUTHORITY + "/config";

    public static final String KEY_RADIUS = "radius";
    public static final String KEY_PAD = "pad";
    public static final String KEY_ICON = "icon";

    public static final int DEF_RADIUS = 8;
    public static final int DEF_PAD = 12;
    public static final int DEF_ICON = 1;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SharedPreferences p = getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        MatrixCursor c = new MatrixCursor(new String[]{KEY_RADIUS, KEY_PAD, KEY_ICON});
        c.addRow(new Object[]{
                p.getInt(KEY_RADIUS, DEF_RADIUS),
                p.getInt(KEY_PAD, DEF_PAD),
                p.getInt(KEY_ICON, DEF_ICON)});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
