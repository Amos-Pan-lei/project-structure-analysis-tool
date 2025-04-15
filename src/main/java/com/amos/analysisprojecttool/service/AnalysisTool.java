package com.amos.analysisprojecttool.service;

import cn.hutool.core.util.StrUtil;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallChain;
import com.amos.analysisprojecttool.bean.res.*;
import com.amos.analysisprojecttool.database.mapper.TableJavaMethodMappingNoteMapper;
import com.amos.analysisprojecttool.database.pojo.TableJavaMethodMappingNote;
import com.amos.analysisprojecttool.service.sql.MapperFindResult;
import com.amos.analysisprojecttool.service.sql.SqlXmlService;
import com.amos.analysisprojecttool.util.AnalysisClassUtils;
import com.amos.analysisprojecttool.util.CallGraphUtils;
import com.amos.analysisprojecttool.util.MdGenMdUtil;
import com.amos.analysisprojecttool.util.MybatisPlusUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 解析工具
 */
@Slf4j
@Service
@Setter
public class AnalysisTool {


    @Autowired
    private SootUpByteCodeAnalysis sootUpByteCodeAnalysis;

    @Autowired
    private SqlXmlService sqlXmlService;

    @Autowired
    private TableJavaMethodMappingNoteMapper noteMapper;


    public List<ClassInfoRes> allClassInfo(String classNameSearch, String methodName) {
        HashMultimap<String, MethodCallNodeInfo> classInfoMultimap = HashMultimap.create();
        HashMultimap<JavaSootMethod, JavaSootMethod> methodCallMap = sootUpByteCodeAnalysis.getMethodCallMap();
        methodCallMap.forEach((call, called) -> {
            if (!StrUtil.contains(call.getName(), "$")) {
                classInfoMultimap.put(call.getDeclaringClassType().getFullyQualifiedName(), new MethodCallNodeInfo(call));
            }
            if (!StrUtil.contains(called.getName(), "$")) {
                classInfoMultimap.put(called.getDeclaringClassType().getFullyQualifiedName(), new MethodCallNodeInfo(called));
            }
        });
        HashMultimap<JavaSootMethod, JavaSootMethod> methodCallReverseMap = sootUpByteCodeAnalysis.getMethodCallReverseMap();
        methodCallReverseMap.forEach((call, called) -> {
            if (!StrUtil.contains(call.getName(), "$")) {
                classInfoMultimap.put(call.getDeclaringClassType().getFullyQualifiedName(), new MethodCallNodeInfo(call));
            }
            if (!StrUtil.contains(called.getName(), "$")) {
                classInfoMultimap.put(called.getDeclaringClassType().getFullyQualifiedName(), new MethodCallNodeInfo(called));
            }
        });
        Map<String, Collection<MethodCallNodeInfo>> map = classInfoMultimap.asMap();

        List<ClassInfoRes> resList = new ArrayList<>();

        map.forEach((className, methods) -> {
            if (StrUtil.isBlank(classNameSearch) || StrUtil.containsIgnoreCase(className, classNameSearch)) {
                ClassInfoRes classInfoRes = new ClassInfoRes();
                classInfoRes.setClassFullName(className);
                classInfoRes.setMethods(methods);
                resList.add(classInfoRes);
            }
        });
        return resList;
    }

    public MethodCallChainRes callGraph(String classFullyName, String methodName) {
        MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallGraph(classFullyName, methodName);
        return MethodCallChainRes.copyMethodCallChain(methodCallChain);
    }


    public MethodCallChainRes callGraphReverse(String classFullyName, String methodName) {
        MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallReverseGraph(classFullyName, methodName);
        return MethodCallChainRes.copyMethodCallChain(methodCallChain);
    }


    ExecutorService executorService = Executors.newCachedThreadPool();

    public Map<String, String> queryTableMapperCallGraphBatchToMd(List<String> tableNames, boolean ifGenMermaidText) {
        Map<String, String> resultMap = new ConcurrentHashMap<>();
        ArrayList<CompletableFuture<Void>> tasks = Lists.newArrayList();
        for (String tableName : tableNames) {
            CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
                String s = queryTableMapperCallGraphToMd(tableName, ifGenMermaidText);
                resultMap.put(tableName, s);
            }, executorService);
            tasks.add(voidCompletableFuture);
        }

        for (CompletableFuture<Void> task : tasks) {
            task.join();
        }
        log.info("所有表都解析结束了  table = {}", tableNames);
        return resultMap;
    }

    /**
     * 搜索表涉及的所有 可用的java方法
     *
     * @param tableName
     * @return
     */
    public List<MethodCallChain> queryAlCallReverseGraphByTableName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return Collections.emptyList();
        }
        List<MethodCallChain> callChainList = Lists.newArrayList();
        //从注解的sql中寻找 method
        List<JavaSootMethod> javaSootMethods = sootUpByteCodeAnalysis.searchMybatisAnnotationMethodsByTableName(tableName);
        for (JavaSootMethod javaSootMethod : javaSootMethods) {
            MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallReverseGraph(javaSootMethod);
            callChainList.add(methodCallChain);
        }

        //从xml的sql中寻找 method节点
        List<MapperFindResult> resultList = sqlXmlService.searchMapperByTableName(tableName);
        for (MapperFindResult mapperFindResult : resultList) {
            String mapperJavaName = mapperFindResult.getMapperJavaName();
            for (String sqlId : mapperFindResult.getSqlIds()) {
                MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallReverseGraph(mapperJavaName, sqlId);
                callChainList.add(methodCallChain);
            }
        }
        Set<JavaSootClass> mybatisPlusMapperByTable = sootUpByteCodeAnalysis.findMybatisPlusMapperByTable(tableName);
        for (JavaSootClass plusMapperClass : mybatisPlusMapperByTable) {
            log.info("mybatisPlus  MapperClass = {}", plusMapperClass.getName());

            Set<JavaSootMethod> mapperMethods = sootUpByteCodeAnalysis.queryAllMethods(plusMapperClass.getName());
            for (JavaSootMethod mapperMethod : mapperMethods) {
                //mapper 所有的方法都可以作为 调用链路的起点
                log.info("mybatisPlus MapperMethod = {}", mapperMethod.getName());
                MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallReverseGraph(mapperMethod);
                callChainList.add(methodCallChain);
            }
        }
        Set<JavaSootClass> mybatisPlusServiceByTable = sootUpByteCodeAnalysis.findMybatisPlusServiceByTable(tableName);
        for (JavaSootClass plusServiceClass : mybatisPlusServiceByTable) {
            log.info("mybatisPlus  ServiceClass = {}", plusServiceClass.getName());

            Set<JavaSootMethod> mapperMethods = sootUpByteCodeAnalysis.queryAllMethods(plusServiceClass.getName());

            for (JavaSootMethod serviceMethod : mapperMethods) {
                //service的  只取  从 mybatisPlus继承的 方法作为起点

                log.info("mybatisPlus ServiceMethod = {}", serviceMethod.getName());
                if (MybatisPlusUtils.isPlusServiceImplMethod(serviceMethod)) {
                    MethodCallChain methodCallChain = sootUpByteCodeAnalysis.queryCallReverseGraph(serviceMethod);
                    callChainList.add(methodCallChain);
                }

            }
        }
        callChainList.removeIf(chain -> Objects.isNull(chain.getHead()));
        HashMap<JavaSootMethod, MethodCallChain> distinctMap = new HashMap<>();

        for (MethodCallChain methodCallChain : callChainList) {
            distinctMap.putIfAbsent(methodCallChain.getHead().getCurrent(), methodCallChain);
        }
        callChainList = distinctMap.values().stream().sorted(Comparator.comparing(chain -> chain.getHead().getCurrent().getDeclaringClassType().toString())).collect(Collectors.toList());
        return callChainList;
    }


    public String queryTableMapperCallGraphToMd(String tableName, boolean ifGenMermaidText) {
        if (StrUtil.isBlank(tableName)) {
            return StrUtil.EMPTY;
        }
        tableName = StrUtil.trim(tableName);
        List<MethodCallChain> callChainList = queryAlCallReverseGraphByTableName(tableName);
        String mdtext = chainToMarkdown(tableName, ifGenMermaidText, callChainList);
        return mdtext;

    }

    public static String chainToMarkdown(String title, boolean ifGenMermaidText, List<MethodCallChain> callChainList) {
        log.info("{} 总共 {} 个 调用链 ", title, callChainList.size());

        MdGenMdUtil.SectionBuilder mdBuilder = MdGenMdUtil.of()
                //h2
                .bigTitle(title)
                .text("\n")
                .ref()
                .text(String.format("总共 %s 个 调用链 ", callChainList.size()))
                .endRef();

        Map<String, List<MethodCallChain>> groupByModule = callChainList.stream().collect(Collectors.groupingBy(chain -> AnalysisClassUtils.extractModuleName(chain.getHead().getCurrent().getDeclaringClassType())));

        int chainCount = 0;
        for (Map.Entry<String, List<MethodCallChain>> moduleEntry : groupByModule.entrySet()) {

            //一层循环 按照模块
            String moduleName = moduleEntry.getKey();

            Map<String, List<MethodCallChain>> groupByClassName = moduleEntry.getValue().stream().collect(Collectors.groupingBy(chain -> chain.getHead().getCurrent().getDeclaringClassType().getClassName()));

            //h3
            mdBuilder.title(String.format("模块 %s ", moduleName))
                    .text("\n ");

            for (Map.Entry<String, List<MethodCallChain>> classEntry : groupByClassName.entrySet()) {
                //二层循环 按照类名
                String className = classEntry.getKey();
                //h4
                mdBuilder.subTitle(String.format("类： %s ", className)).text("\n");

                List<MethodCallChain> chainList = classEntry.getValue();

                for (MethodCallChain methodCallChain : chainList) {
                    chainCount++;
                    mdBuilder.text("\n")
                            .text("\n");
                    //h5
                    mdBuilder.text(String.format("##### 第 %s 个: 查询函数调用图 %s", chainCount, methodCallChain.getHead().getCurrent().getName()))
                            .text("\n");
                    MethodCallChainRes methodCallChainRes = MethodCallChainRes.copyMethodCallChain(methodCallChain);
                    String tab = "\t";
                    CallGraphUtils.traverseTree(methodCallChainRes.getHead(), (nodeRes, depth) -> {

                        StringBuilder tabPrefix = new StringBuilder();
                        for (Integer integer = 0; integer < depth - 1; integer++) {
                            tabPrefix.append(tab);
                        }
                        mdBuilder.text(tabPrefix + "- [ ] " + nodeRes.getCurrent().getNodeRemark());
                    });

                    mdBuilder.text("\n")
                            .text("\n");
                    if (!methodCallChainRes.isEmpty() && ifGenMermaidText) {
                        String mermaidText = CallGraphUtils.graphChainToMermaidText(methodCallChainRes);
                        mdBuilder.text("```mermaid")
                                .text(mermaidText)
                                .text("```");

                    }
                }
            }


        }
        String mdtext = mdBuilder.build();
        return mdtext;
    }

    /**
     * 查询某个表涉及到的接口
     *
     * @param tableName
     * @return
     */
    public Set<MethodAndEndPointResult> oneTableMappingEndPoints(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return Collections.emptySet();
        }
        List<MethodCallChain> callChainList = queryAlCallReverseGraphByTableName(tableName);
        Set<MethodAndEndPointResult> endPointMappings = new HashSet<>();
        for (MethodCallChain methodCallChain : callChainList) {
            methodCallChain.visit(node -> {
                String endPointUrl = node.getEndPointUrl();
                if (StrUtil.isNotBlank(endPointUrl)) {
                    String moduleName = AnalysisClassUtils.extractModuleName(node.getCurrent().getDeclaringClassType());
                    MethodAndEndPointResult methodAndEndPointResult = new MethodAndEndPointResult()
                            .setEndPointUrl(endPointUrl)
                            .setModuleName(moduleName)
                            .setTableName(tableName)
                            .setMethodSignature(node.getCurrent().getSignature().toString());
                    endPointMappings.add(methodAndEndPointResult);
                }
            });
        }
        return endPointMappings;
    }


    Map<String, String> moduleContextPathMap = new HashMap<>();

    {
        moduleContextPathMap.put("bam", "/bam");
        moduleContextPathMap.put("compliance", "/compliance");
        moduleContextPathMap.put("im", "/im/V1.0");
        moduleContextPathMap.put("news", "/news");
        moduleContextPathMap.put("sso", "/sso/V1.0");
        moduleContextPathMap.put("transaction", "/transaction");
        moduleContextPathMap.put("transfer_account", "/transfer/account");
        moduleContextPathMap.put("user", "/user");
        moduleContextPathMap.put("web", "/server/V1.0");
        moduleContextPathMap.put("websocket", "/websocket");
        moduleContextPathMap.put("lifecycle_manage", "/lifecycle/management");
        moduleContextPathMap.put("project_manager", "/project");
        moduleContextPathMap.put("private_equity", "/private/equity");
        moduleContextPathMap.put("data_report", "/dataReport");
        moduleContextPathMap.put("organization", "/organization");
        moduleContextPathMap.put("dashboard", "/dashboard");
        moduleContextPathMap.put("special_service", "/special/service");
        moduleContextPathMap.put("right", "/right");
        moduleContextPathMap.put("file_server", "/file");
        moduleContextPathMap.put("background", "/background");
        moduleContextPathMap.put("newsPlate", "/newsPlate");
        moduleContextPathMap.put("risk", "/risk");
        moduleContextPathMap.put("ltv_cof", "/ltvcof");
        moduleContextPathMap.put("member", "/member");
        moduleContextPathMap.put("community", "/community");
    }


    /**
     * 刷新table 对应的接口的笔记
     *
     * @param tableNames
     */
    public void flushTableMappingMethodsNote(List<String> tableNames) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String tableName : tableNames) {
            CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
                List<TableJavaMethodMappingNote> tableJavaMethodMappingNotes = queryTableMappingMethods(tableName);
                for (TableJavaMethodMappingNote tableJavaMethodMappingNote : tableJavaMethodMappingNotes) {
                    noteMapper.insertOrUpdate(tableJavaMethodMappingNote);
                }
            }, executorService);

            tasks.add(voidCompletableFuture);
        }
        for (CompletableFuture<Void> task : tasks) {
            task.join();
        }
        log.info("tables all note flush ok...{}", tableNames);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<TableJavaMethodMappingNote> queryTableMappingMethods(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return Collections.emptyList();
        }
        ArrayList<TableJavaMethodMappingNote> resultList = Lists.newArrayList();

        //从注解的sql中寻找 method
        List<JavaSootMethod> javaSootMethods = sootUpByteCodeAnalysis.searchMybatisAnnotationMethodsByTableName(tableName);

        for (JavaSootMethod javaSootMethod : javaSootMethods) {
            String moduleName = AnalysisClassUtils.extractModuleName(javaSootMethod.getDeclaringClassType());
            TableJavaMethodMappingNote tableJavaMethodMappingNote = new TableJavaMethodMappingNote();
            tableJavaMethodMappingNote.setTableName(tableName);
            tableJavaMethodMappingNote.setModuleName(moduleName);
            tableJavaMethodMappingNote.setClassName(javaSootMethod.getDeclaringClassType().getClassName());
            tableJavaMethodMappingNote.setMethodName(javaSootMethod.getName());
            tableJavaMethodMappingNote.setMethodSignature(javaSootMethod.getSignature().getSubSignature().toString());
            resultList.add(tableJavaMethodMappingNote);
        }

        //从xml的sql中寻找 method节点
        List<MapperFindResult> xmlmapperMethods = sqlXmlService.searchMapperByTableName(tableName);
        for (MapperFindResult xmlmapperMethod : xmlmapperMethods) {
            String mapperJavaName = xmlmapperMethod.getMapperJavaName();
            String moduleName = AnalysisClassUtils.extractModuleName(mapperJavaName);
            for (String sqlId : xmlmapperMethod.getSqlIds()) {
                TableJavaMethodMappingNote tableJavaMethodMappingNote = new TableJavaMethodMappingNote();
                tableJavaMethodMappingNote.setTableName(tableName);
                tableJavaMethodMappingNote.setClassName(AnalysisClassUtils.extractClassName(mapperJavaName));
                tableJavaMethodMappingNote.setModuleName(moduleName);
                tableJavaMethodMappingNote.setMethodName(sqlId);
                Set<JavaSootMethod> methodSet = sootUpByteCodeAnalysis.queryAllMethods(mapperJavaName);
                for (JavaSootMethod javaSootMethod : methodSet) {
                    if (javaSootMethod.getName().equals(sqlId)) {
                        tableJavaMethodMappingNote.setMethodSignature(javaSootMethod.getSignature().getSubSignature().toString());
                        resultList.add(tableJavaMethodMappingNote);
                        break;
                    }
                }

            }
        }
        return resultList;
    }

    public String queryCallGraphToMdTxt(String direction, String classFullyName, String methodName, boolean genMermaidText) {
        MethodCallChain callChain;
        if (StrUtil.equals(direction, "down")) {
            callChain = sootUpByteCodeAnalysis.queryCallGraph(classFullyName, methodName);
        } else {
            callChain = sootUpByteCodeAnalysis.queryCallReverseGraph(classFullyName, methodName);
        }

        String content = chainToMarkdown(classFullyName + "#" + methodName, genMermaidText, Collections.singletonList(callChain));
        return content;
    }

    public Set<String> allTables() {
        Set<String> tables = new HashSet<>();
        tables.addAll(sootUpByteCodeAnalysis.getTableNameAndMybatisPlusServiceClassMap().keySet());
        tables.addAll(sootUpByteCodeAnalysis.getTableNameAndMybatisPlusPojoClassMap().keySet());
        HashMultimap<JavaSootMethod, String> annotationMethodTableNameMap = sootUpByteCodeAnalysis.getAnnotationMethodTableNameMap();
        tables.addAll(annotationMethodTableNameMap.values());
        tables.addAll(sqlXmlService.getAllSqlTables());
        return tables;
    }

    public Collection<EndpointRes> allEndPoints() {
        Map<JavaSootMethod, String> endPointMethodUriMap = sootUpByteCodeAnalysis.getEndPointMethodUriMap();
        List<EndpointRes> reslist = new ArrayList<>();
        HashMultimap<@Nullable String, @Nullable MethodCallNodeInfo> uriHashTable = HashMultimap.create();
        endPointMethodUriMap.forEach((method, uri) -> {
            uriHashTable.put(uri, new MethodCallNodeInfo(method));
        });
        uriHashTable.asMap().forEach((uri, methods) -> {
            EndpointRes endpointRes = new EndpointRes();
            endpointRes.setUri(uri);
            endpointRes.setMethods(methods);
            reslist.add(endpointRes);
        });
        return reslist;
    }
}
