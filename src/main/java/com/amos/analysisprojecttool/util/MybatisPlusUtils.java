package com.amos.analysisprojecttool.util;

import sootup.core.types.Type;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MybatisPlusUtils {

    private MybatisPlusUtils() {
    }

    public static String BaseMapper_CLASSNAME = "com.baomidou.mybatisplus.core.mapper.BaseMapper";

    public static String ServiceImpl_CLASSNAME = "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl";
    public static String IService_CLASSNAME = "com.baomidou.mybatisplus.extension.service.IService";

    private static JavaSootClass mybatisPlusBaseMapperClass = null;
    private static JavaSootClass mybatisPlusServiceImplClass = null;
    private static JavaSootClass mybatisPlusIServiceClass = null;

    private static Set<JavaSootMethod> baseMapperMethods = new HashSet<>();
    private static Set<JavaSootMethod> plusServiceImplMethods = new HashSet<>();
    private static Set<JavaSootMethod> IServiceMethods = new HashSet<>();

    public static JavaSootClass getMybatisPlusBaseMapperClass() {
        return mybatisPlusBaseMapperClass;
    }

    public static JavaSootClass getMybatisPlusServiceImplClass() {
        return mybatisPlusServiceImplClass;
    }

    public static void setMybatisPlusBaseMapperClass(JavaSootClass mybatisPlusBaseMapperClass) {
        MybatisPlusUtils.mybatisPlusBaseMapperClass = mybatisPlusBaseMapperClass;
        MybatisPlusUtils.baseMapperMethods = new HashSet(mybatisPlusBaseMapperClass.getMethods());

    }

    public static void setMybatisPlusServiceImplClass(JavaSootClass mybatisPlusServiceImplClass) {
        MybatisPlusUtils.mybatisPlusServiceImplClass = mybatisPlusServiceImplClass;
        MybatisPlusUtils.plusServiceImplMethods = new HashSet(mybatisPlusServiceImplClass.getMethods());
    }

    public static void setMybatisPlusIServiceClass(JavaSootClass mybatisPlusIServiceClass) {
        MybatisPlusUtils.mybatisPlusIServiceClass = mybatisPlusIServiceClass;
        MybatisPlusUtils.IServiceMethods = new HashSet(mybatisPlusIServiceClass.getMethods());
    }

    public static Set<JavaSootMethod> getBaseMapperMethods() {
        return new HashSet(baseMapperMethods);
    }

    public static Set<JavaSootMethod> getServiceImplMethods() {
        return new HashSet(plusServiceImplMethods);
    }

    public static boolean isPlusServiceImplMethod(JavaSootMethod method) {
        if (Objects.isNull(method)) {
            return false;
        }
        for (JavaSootMethod plusServiceImplMethod : plusServiceImplMethods) {
            boolean nameCheck = plusServiceImplMethod.getName().equals(method.getName());
            List<Type> parameterTypes = method.getParameterTypes();
            List<Type> plusServiceMethodParameterTypes = plusServiceImplMethod.getParameterTypes();
            boolean typeCheck = true;
            for (Type parameterType : parameterTypes) {
                typeCheck = plusServiceMethodParameterTypes.contains(parameterType);
            }
            if (nameCheck && (parameterTypes.size() == plusServiceMethodParameterTypes.size()) && typeCheck) {
                //方法名 参数名称 参数类型都一致 表示为相同
                return true;
            }
        }
        for (JavaSootMethod plusServiceImplMethod : IServiceMethods) {
            boolean nameCheck = plusServiceImplMethod.getName().equals(method.getName());
            List<Type> parameterTypes = method.getParameterTypes();
            List<Type> plusServiceMethodParameterTypes = plusServiceImplMethod.getParameterTypes();
            boolean typeCheck = true;
            for (Type parameterType : parameterTypes) {
                typeCheck = plusServiceMethodParameterTypes.contains(parameterType);
            }
            if (nameCheck && (parameterTypes.size() == plusServiceMethodParameterTypes.size()) && typeCheck) {
                //方法名 参数名称 参数类型都一致 表示为相同
                return true;
            }
        }

        return false;
    }

}
