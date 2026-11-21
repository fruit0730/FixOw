package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {
    public static XC_MethodReplacement returnConstant(Object value) {
        return null;
    }

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}