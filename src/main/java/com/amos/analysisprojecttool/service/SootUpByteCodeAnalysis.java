package com.amos.analysisprojecttool.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallChain;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallGraphNode;
import com.amos.analysisprojecttool.util.AnalysisClassUtils;
import com.amos.analysisprojecttool.util.MyAsmUtil;
import com.amos.analysisprojecttool.util.MybatisPlusUtils;
import com.amos.analysisprojecttool.util.SqlAnalysisUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Sets;
import com.amos.analysisprojecttool.config.AnalysisProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.springframework.beans.factory.annotation.Autowired;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.common.constant.MethodHandle;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootField;
import sootup.core.model.SootMethod;
import sootup.core.model.SourceType;
import sootup.core.signatures.MethodSignature;
import sootup.core.typehierarchy.ViewTypeHierarchy;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.bytecode.frontend.AsmMethodSource;
import sootup.java.bytecode.frontend.AsmUtil;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.PathBasedAnalysisInputLocation;
import sootup.java.core.*;
import sootup.java.core.language.JavaLanguage;
import sootup.java.core.types.AnnotationType;
import sootup.java.core.views.JavaView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * sootup 解析字节码的方式
 */
@Slf4j
public class SootUpByteCodeAnalysis {

    public SootUpByteCodeAnalysis(List<String> targetDirs, List<String> jarLibs) {
        this.targetDirs = targetDirs;
        this.jarLibs = jarLibs;
    }

    public SootUpByteCodeAnalysis(List<String> targetDirs) {
        this.targetDirs = targetDirs;
    }

    public SootUpByteCodeAnalysis() {
    }

    @Autowired
    private AnalysisProperties analysisProperties;

    /**
     * 输入 目标 字节码目录[]
     */
    private List<String> targetDirs = Collections.emptyList();
    /**
     * 基础 jar 包目录
     */
    private List<String> jarLibs = Collections.emptyList();

    @Getter
    private JavaProject project;

    @Getter
    private JavaView view;
    private ViewTypeHierarchy typeHierarchy;

    /**
     * 所有方法父子引用关系
     * 父 -- 子[]
     */
    private HashMultimap<JavaSootMethod, JavaSootMethod> methodCallMap = HashMultimap.create();

    /**
     * 方法引用  子 - 父关系
     * 子 -- 父[]
     */
    private HashMultimap<JavaSootMethod, JavaSootMethod> methodCallReverseMap = HashMultimap.create();

    /**
     * 保存解析到的类 与 类注解的关系
     */
    private HashMultimap<JavaSootClass, AnnotationUsage> classAnnotationUsageMap = HashMultimap.create();


    /**
     * 加了注解@mapper的类 集合
     */
    private Set<JavaSootClass> annotationMapperClasses = new HashSet<>();

    /**
     * method对应注解的映射Map
     */
    private HashMultimap<JavaSootMethod, AnnotationUsage> methodAnnotationpMap = HashMultimap.create();

    /**
     * web的接口 method对应的  uri
     */
    @Getter
    private Map<JavaSootMethod, String> endPointMethodUriMap = new HashMap<>();

    /**
     * 查询method对应的 web url
     *
     * @param method
     * @return
     */
    public String queryMethodUri(JavaSootMethod method) {
        return endPointMethodUriMap.get(method);
    }

    private final Set<String> webAnnotationFullyNames = Sets.newHashSet(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.DeleteMapping"
    );

    /**
     * 注解实现的sql 方法雨表名 映射map
     */
    @Getter
    private HashMultimap<JavaSootMethod, String> annotationMethodTableNameMap = HashMultimap.create();

    /**
     * 查询 mapper方法注解上的表名
     *
     * @param method
     * @return
     */
    public Set<String> queryTablesByMethod(JavaSootMethod method) {
        return annotationMethodTableNameMap.get(method);
    }

    private final Set<String> sqlAnnotations = Sets.newHashSet(
            "org.apache.ibatis.annotations.Delete",
            "org.apache.ibatis.annotations.Select",
            "org.apache.ibatis.annotations.Update",
            "org.apache.ibatis.annotations.Insert"
    );


    /**
     * 所有方法节点
     */
    private Set<JavaSootMethod> allMethodNodeSet = new HashSet<>();

    /**
     * 收集到的 类对应方法map
     */
    private HashMultimap<String, JavaSootMethod> classFullyNameAndMethodsMap = HashMultimap.create();
    private Set<String> excludeMethods = Sets.newHashSet();



    /* mybatis -plus 写法解析结果 */
    /**
     * 表名 与 对应 @TableName 注解的实体类 映射map
     */
    private HashMultimap<String, JavaSootClass> tableNameAndMybatisPlusPojoClassMap = HashMultimap.create();

    /**
     * 通过表名 寻找对应的 @TableName 的实体类
     *
     * @param tableName
     * @return
     */
    public Set<JavaSootClass> findMybatisPlusPojoByTable(String tableName) {
        Set<JavaSootClass> javaSootClasses = tableNameAndMybatisPlusPojoClassMap.get(tableName);
        return javaSootClasses;
    }

    /**
     * 表名 与 对应ServiceImpl   映射 map
     */
    private HashMultimap<String, JavaSootClass> tableNameAndMybatisPlusServiceClassMap = HashMultimap.create();

    /**
     * 通过表名 寻找对应的 Plus的service 类
     *
     * @param tableName
     * @return
     */
    public Set<JavaSootClass> findMybatisPlusServiceByTable(String tableName) {
        Set<JavaSootClass> javaSootClasses = tableNameAndMybatisPlusServiceClassMap.get(tableName);
        return javaSootClasses;
    }


    /**
     * 表名 与 对应BaseMapper   映射 map
     */
    private HashMultimap<String, JavaSootClass> tableNameAndMybatisPlusMapperClassMap = HashMultimap.create();


    /**
     * 通过表名 寻找对应的 Plus的Mapper 类
     *
     * @param tableName
     * @return
     */
    public Set<JavaSootClass> findMybatisPlusMapperByTable(String tableName) {
        Set<JavaSootClass> javaSootClasses = tableNameAndMybatisPlusMapperClassMap.get(tableName);
        return javaSootClasses;
    }

    /* mybatis -plus 写法解析结果 */

    /**
     * 查询函数调用图
     *
     * @param classFullQualifyName
     * @param methodName
     * @return
     */
    public MethodCallChain queryCallGraph(String classFullQualifyName, String methodName) {
        MethodCallChain methodCallChain = new MethodCallChain();
        JavaSootMethod head = null;
        for (JavaSootMethod sootMethod : classFullyNameAndMethodsMap.get(classFullQualifyName)) {
            if (sootMethod.getName().equals(methodName)) {
                head = sootMethod;
                break;
            }
        }
        if (Objects.isNull(head)) {
            return methodCallChain;
        }
        MethodCallGraphNode headNode = new MethodCallGraphNode(head);
        methodCallChain.setHead(headNode);
        findChildMethodNodes(headNode, new HashSet<>());
        return methodCallChain;
    }

    /**
     * 查询函数调用图
     *
     * @param head
     * @return
     */
    public MethodCallChain queryCallGraph(JavaSootMethod head) {
        MethodCallChain methodCallChain = new MethodCallChain();
        if (Objects.isNull(head)) {
            return methodCallChain;
        }
        MethodCallGraphNode headNode = new MethodCallGraphNode(head);
        methodCallChain.setHead(headNode);
        findChildMethodNodes(headNode, new HashSet<>());
        return methodCallChain;
    }

    /**
     * 递归寻找 父 -> 子 引用关系
     *
     * @param callGraphNode
     * @param workedNodeSet
     */
    public void findChildMethodNodes(MethodCallGraphNode callGraphNode, Set<JavaSootMethod> workedNodeSet) {
        JavaSootMethod current = callGraphNode.getCurrent();
        if (workedNodeSet.contains(current)) {
            return;
        }
        workedNodeSet.add(current);
        Set<JavaSootMethod> childMethods;
        callGraphNode.setEndPointUrl(queryMethodUri(current));

        if (current.isAbstract()) {
            //抽象方法 跳转到 对于的 实现方法去
            childMethods = new HashSet<>(findImplMethod(current));
        } else {
            childMethods = methodCallMap.get(current);
        }
        if (CollUtil.isEmpty(childMethods)) {
            return;
        }
        List<MethodCallGraphNode> childs = childMethods.stream()
                .map(x -> new MethodCallGraphNode(x)).collect(Collectors.toList());
        callGraphNode.setChilds(childs);
        for (MethodCallGraphNode child : childs) {
            findChildMethodNodes(child, workedNodeSet);
        }
    }

    /**
     * 查询函数调用图 - 子找父
     *
     * @param classFullQualifyName
     * @param methodName
     * @return
     */
    public MethodCallChain queryCallReverseGraph(String classFullQualifyName, String methodName) {
        MethodCallChain methodCallChain = new MethodCallChain();
        JavaSootMethod head = null;
        for (JavaSootMethod sootMethod : classFullyNameAndMethodsMap.get(classFullQualifyName)) {
            if (sootMethod.getName().equals(methodName)) {
                head = sootMethod;
                break;
            }
        }
        if (Objects.isNull(head)) {
            return methodCallChain;
        }
        MethodCallGraphNode headNode = new MethodCallGraphNode(head);
        methodCallChain.setHead(headNode);
        findChildReverseMethodNodes(headNode, new HashSet<>());
        return methodCallChain;
    }

    /**
     * 查询函数调用图 - 子找父
     *
     * @param head
     * @return
     */
    public MethodCallChain queryCallReverseGraph(JavaSootMethod head) {
        MethodCallChain methodCallChain = new MethodCallChain();
        if (Objects.isNull(head)) {
            return methodCallChain;
        }
        MethodCallGraphNode headNode = new MethodCallGraphNode(head);
        methodCallChain.setHead(headNode);
        findChildReverseMethodNodes(headNode, new HashSet<>());
        return methodCallChain;
    }

    /**
     * 递归寻找 子 -> 父引用关系
     *
     * @param callGraphNode
     * @param workedNodeSet
     */
    public void findChildReverseMethodNodes(MethodCallGraphNode callGraphNode, Set<JavaSootMethod> workedNodeSet) {
        JavaSootMethod current = callGraphNode.getCurrent();
        if (workedNodeSet.contains(current)) {
            return;
        }
        workedNodeSet.add(current);
        callGraphNode.setEndPointUrl(queryMethodUri(current));

        List<JavaSootMethod> abstractMethods = findAbstractMethod(current);
        HashSet<JavaSootMethod> childMethods = new HashSet<>(methodCallReverseMap.get(current));
        childMethods.addAll(abstractMethods);
        if (CollUtil.isEmpty(childMethods)) {
            return;
        }
        List<MethodCallGraphNode> childs = childMethods.stream()
                .map(x -> new MethodCallGraphNode(x)).collect(Collectors.toList());
        callGraphNode.setChilds(childs);
        for (MethodCallGraphNode child : childs) {
            findChildReverseMethodNodes(child, workedNodeSet);
        }
    }

    public Set<JavaSootMethod> queryAllMethods(String classFullyQualifiedName) {
        Set<JavaSootMethod> sootMethods = classFullyNameAndMethodsMap.get(classFullyQualifiedName);
        return sootMethods;
    }

    /**
     * 初始化project
     *
     * @return
     */
    public JavaProject init() {
        log.info("加载以下目录的字节码文件 :\n{}", targetDirs);
        log.info("加载以下目录的jar包 :\n{}", jarLibs);

        log.info("analysisProperties :{}", analysisProperties);
        JavaLanguage language = new JavaLanguage(8);
        JavaProject.JavaProjectBuilder builder = JavaProject.builder(language);
        for (String onePath : targetDirs) {
            builder.addInputLocation(new PathBasedAnalysisInputLocation(Paths.get(onePath), SourceType.Library));
        }
        for (String jarLib : jarLibs) {
            builder.addInputLocation(new JavaClassPathAnalysisInputLocation(jarLib, SourceType.Library));
        }
        JavaProject project = builder.build();
        this.project = project;
        this.view = project.createView();

        ClassType objectClassType = project.getIdentifierFactory().getClassType("java.lang.Object");
        view.getClass(objectClassType).ifPresent(obj -> {
            for (JavaSootMethod method : obj.getMethods()) {
                excludeMethods.add(method.getSignature().getName());
            }
        });

        view.getClass(project.getIdentifierFactory().getClassType(MybatisPlusUtils.BaseMapper_CLASSNAME)).ifPresent(obj -> {
            MybatisPlusUtils.setMybatisPlusBaseMapperClass(obj);
        });
        view.getClass(project.getIdentifierFactory().getClassType(MybatisPlusUtils.ServiceImpl_CLASSNAME)).ifPresent(obj -> {
            MybatisPlusUtils.setMybatisPlusServiceImplClass(obj);
        });
        view.getClass(project.getIdentifierFactory().getClassType(MybatisPlusUtils.IService_CLASSNAME)).ifPresent(obj -> {
            MybatisPlusUtils.setMybatisPlusIServiceClass(obj);
        });

        //继承关系视图
        this.typeHierarchy = new ViewTypeHierarchy(view);
        log.info("初始化Java Project {}", project);
        return project;
    }


    public boolean isNeedAnalysisClass(JavaSootClass aClass) {
        return StrUtil.startWith(aClass.getName(), analysisProperties.getTypePackageNamePrefix());
    }

    public boolean isNeedValInvokeClassType(Set<Type> fields, ClassType stmtInvokeValClassType) {
        boolean isField = fields.contains(stmtInvokeValClassType) || isMybatisPlusMapper(stmtInvokeValClassType);
        return StrUtil.startWith(stmtInvokeValClassType.getFullyQualifiedName(), analysisProperties.getMethodStmtsValInvokerPackageNamePrefix())
                && isField;
    }

    public boolean isMybatisPlusMapper(ClassType classType) {
        boolean isMybatis_plus_mapper = false;
        Optional<JavaSootClass> aClassOpt = view.getClass(classType);
        if (aClassOpt.isPresent()) {
            Set<? extends ClassType> interfaces = aClassOpt.get().getInterfaces();
            for (ClassType anInterface : interfaces) {
                if (anInterface.getFullyQualifiedName().equals(MybatisPlusUtils.BaseMapper_CLASSNAME)) {
                    isMybatis_plus_mapper = true;
                    break;
                }
            }
        }
        return isMybatis_plus_mapper;
    }

    @Getter
    private boolean started;

    public void startUp() {
        started = false;
        //初始化
        init();
        for (JavaSootClass aClass : view.getClasses()) {
            //输入 允许解析的包名
            if (!isNeedAnalysisClass(aClass)) {
//                log.info("不解析类 : {}", aClass.getName());
                continue;
            }
            //开始解析
            analysisOneClassMethodNodes(aClass, view);
        }
        analysisAnnotationMapper();
        started = true;
        log.info("----------解析class完毕 {}----------", targetDirs);
        log.info("----------解析class完毕 {}----------", jarLibs);
    }


    /**
     * 搜索 注解形式 实现的sql
     *
     * @param tableName
     * @return
     */
    public List<JavaSootMethod> searchMybatisAnnotationMethodsByTableName(String tableName) {
        List<JavaSootMethod> resultList = new ArrayList<>();
        if (StrUtil.isBlank(tableName)) {
            return resultList;
        }
        for (JavaSootClass annotationMapperClass : annotationMapperClasses) {
            Set<? extends JavaSootMethod> sootMethods = annotationMapperClass.getMethods();
            for (JavaSootMethod method : sootMethods) {
                Set<String> tables = queryTablesByMethod(method);
                if (tables.contains(tableName)) {
                    resultList.add(method);
                }
            }
        }
        return resultList;
    }


    private void analysisAnnotationMapper() {
        classAnnotationUsageMap.forEach((sootClass, annotations) -> {
            AnnotationType annotation = annotations.getAnnotation();
            if (annotation.getFullyQualifiedName().equals("org.apache.ibatis.annotations.Mapper") && sootClass.isInterface()) {
                //找到符合条件的mapper class
                annotationMapperClasses.add(sootClass);
            }
        });
    }

    public Set<SootField> findClassAllField(SootClass<JavaSootClassSource> sootClass) {
        ClassType classType = sootClass.getType();
        List<JavaSootClass> superClasses = findSuperClasses(classType);
        List<JavaSootClass> superInterfaces = findSuperInterfaces(classType);
        Set<SootField> fieldSet = new HashSet<>(sootClass.getFields());
        for (JavaSootClass superClassType : superClasses) {
            fieldSet.addAll(superClassType.getFields());
        }
        for (JavaSootClass superClassType : superInterfaces) {
            fieldSet.addAll(superClassType.getFields());
        }
        return fieldSet;
    }

    public Optional<JavaSootClass> findSuperClass(ClassType classType) {
        try {
            ClassType superClassType = typeHierarchy.superClassOf(classType);
            return view.getClass(superClassType);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 寻找实现方法
     *
     * @param abstractMethod
     * @return
     */
    public List<JavaSootMethod> findImplMethod(JavaSootMethod abstractMethod) {
        List<JavaSootMethod> implMethods = new ArrayList<>();

        if (!abstractMethod.isAbstract()) {
            return Collections.emptyList();
        }
        List<JavaSootClass> subClasses = findSubClasses(abstractMethod.getDeclaringClassType());
        for (JavaSootClass subClass : subClasses) {
            Optional<JavaSootMethod> impldMethodOpt = subClass.getMethod(abstractMethod.getName(), abstractMethod.getParameterTypes());

            impldMethodOpt.ifPresent(subMethod -> {
                if (!subMethod.isAbstract()) {
                    implMethods.add(subMethod);
                }
            });
        }
        return implMethods;
    }

    /**
     * 寻找实现方法对应的抽象方法
     *
     * @param implMethod
     * @return
     */
    public List<JavaSootMethod> findAbstractMethod(JavaSootMethod implMethod) {
        List<JavaSootMethod> abstractMethods = new ArrayList<>();
        if (implMethod.isAbstract()) {
            //抽象方法不用寻找
            return Collections.emptyList();
        }
        ClassType declaringClassType = implMethod.getDeclaringClassType();
        List<JavaSootClass> superClasses = findSuperClasses(declaringClassType);
        List<JavaSootClass> superInterfaces = findSuperInterfaces(declaringClassType);
        for (JavaSootClass superClass : superClasses) {
            Optional<JavaSootMethod> abstractMethodOpt = superClass.getMethod(implMethod.getName(), implMethod.getParameterTypes());
            abstractMethodOpt.ifPresent(subMethod -> {
                if (subMethod.isAbstract()) {
                    abstractMethods.add(subMethod);
                }
            });
        }
        for (JavaSootClass superClass : superInterfaces) {
            Optional<JavaSootMethod> abstractMethodOpt = superClass.getMethod(implMethod.getName(), implMethod.getParameterTypes());
            abstractMethodOpt.ifPresent(subMethod -> {
                if (subMethod.isAbstract()) {
                    abstractMethods.add(subMethod);
                }
            });
        }
        return abstractMethods;
    }

    /**
     * 根据全限定类名 获取类
     *
     * @param classFullyName
     * @return
     */
    public Optional<JavaSootClass> findClass(String classFullyName) {
        ClassType classType = project.getIdentifierFactory().getClassType(classFullyName);
        Optional<JavaSootClass> aClass = view.getClass(classType);
        return aClass;
    }

    public Set<AnnotationUsage> findMethodAnnotations(JavaSootMethod method) {
        Set<AnnotationUsage> annotationUsages = methodAnnotationpMap.get(method);
        return annotationUsages;
    }

    public List<JavaSootClass> findSubClasses(ClassType classType) {
        try {
            Set<ClassType> classTypes = typeHierarchy.subtypesOf(classType);
            List<JavaSootClass> subclasses = new ArrayList<>();
            for (ClassType type : classTypes) {
                Optional<JavaSootClass> aClass = view.getClass(type);
                aClass.ifPresent(subClass -> {
                    subclasses.add(subClass);
                });
            }
            return subclasses;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<JavaSootClass> findSuperClasses(ClassType classType) {
        List<JavaSootClass> superClassList = new ArrayList<>();
        Optional<JavaSootClass> currentSuperClass = findSuperClass(classType);
        while (currentSuperClass.isPresent()) {
            superClassList.add(currentSuperClass.get());
            currentSuperClass = findSuperClass(currentSuperClass.get().getType());
        }
        return superClassList;
    }

    public List<JavaSootClass> findSuperInterfaces(ClassType classType) {
        Set<ClassType> classTypes = typeHierarchy.implementedInterfacesOf(classType);
        List<JavaSootClass> superInterfaceList = new ArrayList<>();
        for (ClassType superInterfaceType : classTypes) {
            Optional<JavaSootClass> aClass = view.getClass(superInterfaceType);
            aClass.ifPresent(superInterface -> {
                superInterfaceList.add(superInterface);
            });
        }
        return superInterfaceList;
    }

    /**
     * 解析一个类的方法
     *
     * @param sootClass
     * @param javaView
     */
    private void analysisOneClassMethodNodes(JavaSootClass sootClass, JavaView javaView) {
        Set<JavaSootMethod> methods = new HashSet<>(sootClass.getMethods());
        //解析当前类 的注解
        try {


            Set<AnnotationUsage> classAnnotations = findClassAnnotations(sootClass);

            classAnnotations.stream().filter(annotation -> StrUtil.equals(annotation.getAnnotation().getFullyQualifiedName(), "com.baomidou.mybatisplus.annotation.TableName"))
                    .forEach(annotation -> {
                        Map<String, Object> values = annotation.getValues();
                        Object valueObj = values.get("value");
                        String tableName;
                        if (valueObj instanceof StringConstant) {
                            tableName = ((StringConstant) valueObj).getValue();
                            tableNameAndMybatisPlusPojoClassMap.put(tableName, sootClass);
                        }
                    });

            if (sootClass.isInterface()) {
                //判断是否为 plus的 mapper
                List<JavaSootClass> superInterfaces = findSuperInterfaces(sootClass.getType());

                if (superInterfaces.stream().anyMatch(x -> x.getName().equals(MybatisPlusUtils.BaseMapper_CLASSNAME))) {
                    Class<? extends JavaSootClassSource> curclassReflactClass = sootClass.getClassSource().getClass();
                    Field classNodeField = curclassReflactClass.getDeclaredField("classNode");
                    classNodeField.setAccessible(true);
                    ClassNode o = (ClassNode) classNodeField.get(sootClass.getClassSource());
                    Type pojoClassType = AsmUtil.toJimpleSignature(StrUtil.removePrefix(StrUtil.subBetween(o.signature, "<", ">").split(";")[0], "L"));
                    Optional<JavaSootClass> pojoClassOpt = findClass(pojoClassType.toString());
                    pojoClassOpt.ifPresent(pojo -> {
                        try {
                            String tableNameFromPojo = findTableNameFromPojo(pojo);
                            tableNameAndMybatisPlusMapperClassMap.put(tableNameFromPojo, sootClass);
                        } catch (Exception e) {

                        }
                    });
                    log.info("类 {} 为 plus的mapper", sootClass.getName());
                }
            }
            if (sootClass.isConcrete()) {
                //判断是否为 plus的 service
                List<JavaSootClass> superClasses = findSuperClasses(sootClass.getType());
                if (superClasses.stream().anyMatch(x -> x.getName().equals(MybatisPlusUtils.ServiceImpl_CLASSNAME))) {
                    Class<? extends JavaSootClassSource> curclassReflactClass = sootClass.getClassSource().getClass();
                    Field classNodeField = curclassReflactClass.getDeclaredField("classNode");
                    classNodeField.setAccessible(true);
                    ClassNode o = (ClassNode) classNodeField.get(sootClass.getClassSource());
                    Type pojoClassType = AsmUtil.toJimpleSignature(StrUtil.removePrefix(StrUtil.subBetween(o.signature, "<", ">").split(";")[1], "L"));
                    Optional<JavaSootClass> pojoClassOpt = findClass(pojoClassType.toString());
                    pojoClassOpt.ifPresent(pojo -> {
                        try {
                            String tableNameFromPojo = findTableNameFromPojo(pojo);
                            tableNameAndMybatisPlusServiceClassMap.put(tableNameFromPojo, sootClass);
                            log.info("类 {} 为 表 {} plus的 service",sootClass.getName(), tableNameFromPojo);
                        } catch (Exception e) {

                        }
                    });

                }
            }

        } catch (Exception e) {
            //ignore
        }

        //当前类的所有 field ,包括从父类 (接口)拿到的
        Set<Type> fields = findClassAllField(sootClass)
                .stream()
                .map(SootField::getType)
                .filter(fieldType -> fieldType instanceof ClassType)
                .collect(Collectors.toSet());
        log.info("解析一个类 : " + sootClass.getName());


        //解析 controller
        String controllerUri = extractControllerUri(sootClass);

        for (JavaSootMethod method : methods) {

            // 解析method注解
            try {
                parseMethodAnnotations(method);
            } catch (Exception e) {
                //ignore
            }

            if (!isNeedUnfoldMethod(method)) {
                if (!excludeMethods.contains(method.getName())) {
                    //只要不是排除的方法 都加入到统计里面
                    classFullyNameAndMethodsMap.put(sootClass.getName(), method);
                }
//                log.info("\t方法 {} 不符合,跳过", method.getName());
                continue;
            }
            classFullyNameAndMethodsMap.put(sootClass.getName(), method);
            //处理 web接口方法
            //解析controller 下面的  接口端点
            Set<AnnotationUsage> annotationUsages = methodAnnotationpMap.get(method);
            String webMethodUri = annotationUsages.stream().filter(annotationUsage -> webAnnotationFullyNames.contains(annotationUsage.getAnnotation().getFullyQualifiedName()))
                    .findFirst().map(annotationUsage -> {
                        Object o = annotationUsage.getValues().get("value");
                        if (o instanceof List) {
                            return StrUtil.removeAll(((List<?>) o).get(0).toString(), "\"");
                        } else if (o instanceof String[]) {
                            return StrUtil.removeAll(((String[]) o)[0], "\"");
                        }
                        return StrUtil.EMPTY;
                    }).orElse(StrUtil.EMPTY);
            if (StrUtil.isNotBlank(webMethodUri)) {
                String methodUrl = StrUtil.addPrefixIfNot(StrUtil.addSuffixIfNot(controllerUri, "/"), "/") + StrUtil.removePrefix(webMethodUri, "/");
                endPointMethodUriMap.put(method, methodUrl);
            }


            for (Stmt stmt : method.getBody().getStmts()) {
//                log.info("\n\n");
//                log.info("\t\tStmt =>> {}", stmt);
//                log.info("\t\tStmt 类型： {}", stmt.getClass().getName());
                if (!stmt.containsInvokeExpr()) {
//                    log.info("\t\t******");
                    continue;
                }
                AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
                ClassType stmtInvokeValClassType = invokeExpr.getMethodSignature().getDeclClassType();

                Optional<? extends SootMethod> invokeExprMethodOpt = Optional.empty();

                if (invokeExpr instanceof JDynamicInvokeExpr) {
                    //lambda 调用
                    List<Immediate> bootstrapArgs = ((JDynamicInvokeExpr) invokeExpr).getBootstrapArgs();
                    for (Immediate bootstrapArg : bootstrapArgs) {
                        if (bootstrapArg instanceof MethodHandle) {
                            MethodSignature lambdaMethodSignature = ((MethodHandle) bootstrapArg).getMethodSignature();
                            if (AnalysisClassUtils.isLambdaMethod(lambdaMethodSignature)) {
                                invokeExprMethodOpt = javaView.getMethod(lambdaMethodSignature);
                                break;
                            }
                        }
                    }
                } else if (sootClass.getType().equals(stmtInvokeValClassType) || isNeedValInvokeClassType(fields, stmtInvokeValClassType)) {
                    invokeExprMethodOpt = javaView.getMethod(invokeExpr.getMethodSignature());
                } else {
                    continue;
                }

                JavaSootMethod calledMethod = null;
                if (invokeExprMethodOpt.isPresent()) {
                    calledMethod = (JavaSootMethod) invokeExprMethodOpt.get();
                } else {
                    try {
                        JavaSootMethod parentCallMethod = null;
                        List<JavaSootClass> superClasses = findSuperClasses(stmtInvokeValClassType);
                        for (JavaSootClass superClass : superClasses) {
                            Optional<JavaSootMethod> calledMethodOpt = superClass.getMethod(invokeExpr.getMethodSignature().getSubSignature());
                            if (calledMethodOpt.isPresent()) {
                                parentCallMethod = calledMethodOpt.get();
                                break;
                            }
                        }
                        if (Objects.isNull(parentCallMethod)) {
                            List<JavaSootClass> superInterfaces = findSuperInterfaces(stmtInvokeValClassType);
                            for (JavaSootClass superInterface : superInterfaces) {
                                Optional<JavaSootMethod> calledMethodOpt = superInterface.getMethod(invokeExpr.getMethodSignature().getSubSignature());
                                if (calledMethodOpt.isPresent()) {
                                    parentCallMethod = calledMethodOpt.get();
                                    break;
                                }
                            }
                        }
                        //如果都找不到就 跳过解析当前执行语句
                        if (Objects.isNull(parentCallMethod)) {
                            continue;
                        }
                        MethodSignature methodSignature = project.getIdentifierFactory().getMethodSignature(stmtInvokeValClassType, invokeExpr.getMethodSignature().getSubSignature());
                        Set<JavaSootMethod> existedMethods = classFullyNameAndMethodsMap.get(stmtInvokeValClassType.getFullyQualifiedName());
                        for (JavaSootMethod existedMethod : existedMethods) {
                            if (existedMethod.getSignature().equals(methodSignature)
                            ) {
                                calledMethod = existedMethod;
                                break;
                            }
                        }
                        if (Objects.isNull(calledMethod)) {
                            calledMethod = new JavaSootMethod(parentCallMethod.getBodySource(), methodSignature, parentCallMethod.getModifiers(), parentCallMethod.getExceptionSignatures(), parseMethodAnnotations(parentCallMethod), parentCallMethod.getPosition());
                        }
                    } catch (Exception e) {
                        //异常时 跳过解析当前执行语句
                        continue;
                    }

                }

                methodCallMap.put(method, calledMethod);
                methodCallReverseMap.put(calledMethod, method);
                allMethodNodeSet.add(method);
                allMethodNodeSet.add(calledMethod);

                classFullyNameAndMethodsMap.put(calledMethod.getDeclaringClassType().getFullyQualifiedName(), calledMethod);
            }

//            log.info("\t---------------------------------------");
        }

        //解析完一个类
    }

    /**
     * 解析类的 注解
     *
     * @param sootClass
     * @return
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Set<AnnotationUsage> findClassAnnotations(JavaSootClass sootClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (Objects.isNull(sootClass)) {
            return Collections.emptySet();
        }
        if (classAnnotationUsageMap.containsKey(sootClass)) {
            return classAnnotationUsageMap.get(sootClass);
        }
        Class<? extends JavaSootClassSource> curclassReflactClass = sootClass.getClassSource().getClass();
        Method resolveAnnotationsMethod = curclassReflactClass.getDeclaredMethod("resolveAnnotations");
        resolveAnnotationsMethod.setAccessible(true);
        Object invoke = resolveAnnotationsMethod.invoke(sootClass.getClassSource());
        List<AnnotationUsage> annotations = (List<AnnotationUsage>) invoke;
        for (AnnotationUsage annotation : annotations) {
//                log.info("\t注解: {}", annotation.toString());
            classAnnotationUsageMap.put(sootClass, annotation);
        }
        return new HashSet<>(annotations);
    }

    /**
     * 解析方法的注解
     *
     * @param method
     * @return
     */
    private Set<AnnotationUsage> parseMethodAnnotations(JavaSootMethod method) {
        if (Objects.isNull(method)) {
            return Collections.emptySet();
        }
        if (methodAnnotationpMap.containsKey(method)) {
            return methodAnnotationpMap.get(method);
        }

        String methodName = method.getSignature().getName();
        if (!excludeMethods.contains(methodName) && !StrUtil.contains(methodName, "lambda$") && !method.isNative()) {
            if (method.getBodySource() instanceof AsmMethodSource) {
                List<AnnotationNode> visibleAnnotations = ((AsmMethodSource) method.getBodySource()).visibleAnnotations;
                List<AnnotationUsage> annotationUsages = MyAsmUtil.convertAnnotation(visibleAnnotations);
                for (AnnotationUsage annotationUsage : annotationUsages) {
                    methodAnnotationpMap.put(method, annotationUsage);
                }
                annotationUsages.stream()
                        .filter(x -> sqlAnnotations.contains(x.getAnnotation().getFullyQualifiedName()))
                        .forEach(mybatisAnnotation -> {
                            Map<String, Object> values = mybatisAnnotation.getValues();
                            String sqlText = StrUtil.join("", ((List<String>) values.get("value")));
                            sqlText = StrUtil.removeSuffix(StrUtil.removePrefix(sqlText, "\""), "\"");
                            sqlText = StrUtil.removeAll(sqlText, "\\n");
                            Set<String> tables = SqlAnalysisUtils.parseTablesFromText(sqlText);
                            for (String table : tables) {
                                annotationMethodTableNameMap.put(method, table);
                            }
                        });

                return new HashSet<>(annotationUsages);
            }
        }
        return Collections.emptySet();
    }

    /**
     * 提取controller上面的 web路径
     *
     * @param sootClass
     * @return
     */
    private String extractControllerUri(JavaSootClass sootClass) {
        Set<AnnotationUsage> annotationUsages = classAnnotationUsageMap.get(sootClass);
        String controllerUri = annotationUsages.stream().filter(annotationUsage -> webAnnotationFullyNames.contains(annotationUsage.getAnnotation().getFullyQualifiedName()))
                .findFirst().map(annotationUsage -> {
                    Object o = annotationUsage.getValues().get("value");
                    if (o instanceof List) {
                        return StrUtil.removeAll(((List<?>) o).get(0).toString(), "\"");
                    } else if (o instanceof String[]) {
                        return StrUtil.removeAll(((String[]) o)[0], "\"");
                    }
                    return StrUtil.EMPTY;
                }).orElse(StrUtil.EMPTY);
        return controllerUri;
    }


    public boolean isNeedUnfoldMethod(SootMethod method) {
        String methodName = method.getSignature().getName();
        return method.isConcrete() && !excludeMethods.contains(methodName);
    }

    public String findTableNameFromPojo(JavaSootClass sootClass) throws Exception {
        Set<AnnotationUsage> classAnnotations = findClassAnnotations(sootClass);
        return classAnnotations.stream().filter(annotation -> StrUtil.equals(annotation.getAnnotation().getFullyQualifiedName(), "com.baomidou.mybatisplus.annotation.TableName"))
                .map(annotation -> {
                    Map<String, Object> values = annotation.getValues();
                    Object valueObj = values.get("value");

                    if (valueObj instanceof StringConstant) {
                        return ((StringConstant) valueObj).getValue();

                    }
                    return "";
                }).findFirst().orElse("");
    }
}
