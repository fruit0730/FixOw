package de.robv.android.xposed;

public class XC_MethodHook {
    public static class Unhook {
    }

    public static class MethodHookParam {
        public Object[] args;
        public Object thisObject;

        public void setResult(Object result) {
        }

        public Object getResult() {
            return null;
        }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }
}