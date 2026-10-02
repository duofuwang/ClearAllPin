package de.robv.android.xposed;

import java.util.Set;

public class XposedBridge {

    public static void log(String text) {
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> hookClass, XC_MethodHook callback) {
        return null;
    }
}
