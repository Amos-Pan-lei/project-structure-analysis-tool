package com.amos.analysisprojecttool.service.sql;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class SqlXmlService {
    @Autowired
    SqlXmlParseResultCache sqlXmlParseResultCache;
    @Autowired
    SqlXmlParser sqlXmlParser;

    public Set<String> getAllSqlTables(){
        Map<String, ParseXmlResult> xmlResultMap = sqlXmlParseResultCache.getXmlResultMap();
        Set<String> tables = new HashSet<>();
        for (ParseXmlResult xmlResult : xmlResultMap.values()) {
            for (Collection<String> tableColl : xmlResult.getElementTablesMap().values()) {
                tables.addAll(tableColl);
            }
        }
        return tables;
    }

    public void parseDirXml(String directoryPath) {
        String targetFileNamePattern = ".*\\.xml$";// 指定目标文件名 正则
        List<File> foundFiles = sqlXmlParser.searchFiles(directoryPath, targetFileNamePattern);
        log.info("=======扫描文件位置结束=======");
        log.info("开始解析xml......");
        ArrayList<CompletableFuture<Void>> tasks = Lists.newArrayList();
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        for (File file : foundFiles) {
            CompletableFuture<Void> parseOneFileTask = CompletableFuture.runAsync(() -> {
                sqlXmlParseResultCache.getParseXmlResult(file);
            }, executorService);
            tasks.add(parseOneFileTask);
        }
        for (CompletableFuture<Void> task : tasks) {
            task.join();
        }
        executorService.shutdown();
        log.info("目标文件解析结束 = {}", foundFiles);
    }


    public String findSqlByNameSpaceAndId(String namespace, String sqlId) {
        Map<String, ParseXmlResult> xmlResultMap = sqlXmlParseResultCache.getXmlResultMap();
        ParseXmlResult parseXmlResult = xmlResultMap.get(namespace);
        if (Objects.isNull(parseXmlResult)) {
            return null;
        }

        return parseXmlResult.getElementSqlTextMap().get(sqlId);
    }

    public Set<String> findTableByNamesSpaceAndId(String namespace, String sqlId) {
        Map<String, ParseXmlResult> xmlResultMap = sqlXmlParseResultCache.getXmlResultMap();
        ParseXmlResult parseXmlResult = xmlResultMap.get(namespace);
        if (Objects.isNull(parseXmlResult)) {
            return Collections.emptySet();
        }
        Collection<String> tables = parseXmlResult.getElementTablesMap().get(sqlId);

        if (Objects.isNull(tables)) {
            return new HashSet<>();
        }
        return new HashSet<>(tables);
    }

    public List<MapperFindResult> searchMapperByTableName(String tableName) {
        List<MapperFindResult> findResults = new ArrayList<>();
        Map<String, ParseXmlResult> xmlResultMap = sqlXmlParseResultCache.getXmlResultMap();
        for (ParseXmlResult xmlResult : xmlResultMap.values()) {
            List<String> sqlIds = Lists.newArrayList();
            xmlResult.getElementTablesMap().forEach((sqlId, tables) -> {
                if (tables.contains(tableName)) {
                    sqlIds.add(sqlId);
                }
            });
            if (CollUtil.isNotEmpty(sqlIds)) {
                findResults.add(new MapperFindResult(sqlIds, null, xmlResult.getNameSpace()));
            }
        }
        return findResults;
    }

}
