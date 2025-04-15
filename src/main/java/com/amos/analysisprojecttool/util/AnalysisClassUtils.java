package com.amos.analysisprojecttool.util;

import cn.hutool.core.util.StrUtil;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ClassType;
import sootup.java.core.JavaSootMethod;

public class AnalysisClassUtils {
    private AnalysisClassUtils() {
    }

    public static String extractModuleName(ClassType classType) {
        String packageName = classType.getPackageName().getPackageName();
        return extractModuleName(packageName);
    }

    public static String extractModuleName(String packageName) {
        if (packageName.startsWith("hk.com.easyview")) {
            String s = StrUtil.removePrefix(packageName, "hk.com.easyview.");
            String moduleName = StrUtil.subBefore(s, ".", false);
            return moduleName;
        }
        return StrUtil.EMPTY;
    }

    public static String extractClassName(String packageName) {
        return StrUtil.subAfter(packageName, ".", true);
    }


    public static boolean isLambdaMethod(JavaSootMethod method) {
        String methodName = method.getName();
        return StrUtil.contains(methodName, "lambda$");
    }

    public static boolean isLambdaMethod(MethodSignature method) {
        String methodName = method.getName();
        return StrUtil.contains(methodName, "lambda$");
    }

}
