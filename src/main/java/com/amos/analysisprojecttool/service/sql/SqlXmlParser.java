package com.amos.analysisprojecttool.service.sql;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.amos.analysisprojecttool.util.SqlAnalysisUtils;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SqlXmlParser {


    public ParseXmlResult parseXml(File file) {
        return parseXml(file.getAbsolutePath());
    }

    public ParseXmlResult parseXml(String xmlAbsolutePath) {
        try {
            log.info("解析xml文件 : " + xmlAbsolutePath);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(xmlAbsolutePath));
            //标签名e.g:  <insert>  <update>  <select>  <delete>
            ArrayList<Element> elements = Lists.newArrayList();
            elements.addAll(searchSqlNodeByTagName(document, "insert"));
            elements.addAll(searchSqlNodeByTagName(document, "update"));
            elements.addAll(searchSqlNodeByTagName(document, "select"));
            elements.addAll(searchSqlNodeByTagName(document, "delete"));
            List<Element> sqlElements = searchSqlNodeByTagName(document, "sql");
            HashMap<String, String> sqlNodeMap = new HashMap<>();
            for (Element sqlElement : sqlElements) {
                sqlNodeMap.put(sqlElement.getAttribute("id"), sqlElement.getTextContent());
            }
            HashMultimap<String, String> elementTablesMap = HashMultimap.create();
            Map<String, String> elementSqlTextMap = new HashMap<>();
            for (Element element : elements) {
                String id = element.getAttribute("id");
                String sqlText = parseElementSqlText(element, new StringBuilder(), sqlNodeMap);
                elementSqlTextMap.put(id, sqlText);
                Set<String> tables = SqlAnalysisUtils.parseTablesFromText(sqlText);
                for (String table : tables) {
                    elementTablesMap.put(id, table);
                }
            }
            ParseXmlResult parseXmlResult = new ParseXmlResult();
            parseXmlResult.setNameSpace(document.getDocumentElement().getAttribute("namespace"));
            parseXmlResult.setElementSqlTextMap(elementSqlTextMap);
            parseXmlResult.setElementTablesMap(elementTablesMap.asMap());
            log.info("解析xml文件 : " + xmlAbsolutePath + " 完成");
            return parseXmlResult;
        } catch (Exception e) {
            log.error("解析xml文件报错 " + xmlAbsolutePath);
            e.printStackTrace();
            return null;
        }
    }


    // 递归获取元素及其子元素的文本内容并拼接
    private String parseElementSqlText(Element element, StringBuilder textContent, HashMap<String, String> sqlNodeMap) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                // 递归处理子元素
                Element elementChildNode = (Element) childNode;
                String refid = elementChildNode.getAttribute("refid");
                if (StrUtil.isNotBlank(refid)) {
                    String refSql = sqlNodeMap.get(refid);
                    if (StrUtil.isNotEmpty(refSql)) {
                        //拼接引用的 sql文本
                        textContent.append(refSql);
                    }
                } else {
                    parseElementSqlText((Element) childNode, textContent, sqlNodeMap);
                }
            } else if (childNode.getNodeType() == Node.TEXT_NODE) {
                // 拼接sql文本
                textContent.append(childNode.getTextContent());
            }
        }
        return textContent.toString();
    }


    public List<String> searchElementIncludeSqls(Element sqlElement, HashMap<String, String> sqlNodeMap) {
        ArrayList<String> sqls = new ArrayList<>();
        NodeList childNodes = sqlElement.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            Node item = childNodes.item(j);
            if (item.getNodeType() == Node.ELEMENT_NODE) {
                Element include = (Element) item;
                String refid = include.getAttribute("refid");
                if (StrUtil.isNotBlank(refid)) {
                    String sql = sqlNodeMap.get(refid);
                    if (StrUtil.isNotEmpty(sql)) {
                        sqls.add(sql);
                    }
                }
            }
        }
        sqls.removeIf(StrUtil::isBlank);
        return sqls;
    }

    private List<Element> searchSqlNodeByTagName(Document document, String elementName) {
        ArrayList<Element> elements = new ArrayList<>();
        NodeList sqlNodes = document.getElementsByTagName(elementName);
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Node sqlNode = sqlNodes.item(i);
            if (sqlNode.getNodeType() == Node.ELEMENT_NODE) {
                Element sqlElement = (Element) sqlNode;
                elements.add(sqlElement);
            }
        }
        return elements;
    }


    public List<MapperFindResult> fileSearchXMLForTable(List<String> tableNameList, String directoryPath) {
        String targetFileNamePattern = ".*Mapper\\.xml$";// 指定目标文件名 正则
        List<File> foundFiles = searchFiles(directoryPath, targetFileNamePattern);
        log.info("=======sqlParser扫描文件位置 {} 结束=======", directoryPath);
        log.info("开始解析xml......");
        List<MapperFindResult> resultList = new ArrayList<>();
        for (String tableName : tableNameList) {
            CopyOnWriteArrayList<MapperFindResult> findResults = new CopyOnWriteArrayList<>();
            ArrayList<CompletableFuture<Void>> tasks = Lists.newArrayList();
            for (File file : foundFiles) {
                CompletableFuture<Void> parseOneFileTask = CompletableFuture.runAsync(() -> {
                    ParseXmlResult parseXmlResult = parseXml(file);
                    if (Objects.isNull(parseXmlResult)) {
                        return;
                    }
                    List<String> sqlIds = new ArrayList<>();
                    parseXmlResult.getElementTablesMap().forEach((sqlId, tables) -> {
                        if (tables.contains(tableName)) {
                            sqlIds.add(sqlId);
                        }
                    });
                    if (CollUtil.isNotEmpty(sqlIds)) {
                        findResults.add(new MapperFindResult(sqlIds, file, parseXmlResult.getNameSpace()));
                    }
                });
                tasks.add(parseOneFileTask);
            }
            for (CompletableFuture<Void> task : tasks) {
                task.join();
            }
            resultList.addAll(new ArrayList<>(findResults));
        }

        return resultList;
    }

    public List<File> searchFiles(String directoryPath, String targetFileNamePattern) {
        File directory = new File(directoryPath);
        List<File> foundFilesList = new ArrayList<>();
        Pattern pattern = Pattern.compile(targetFileNamePattern);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    Matcher matcher = pattern.matcher(file.getName());
                    if (file.isFile() && matcher.matches()) {
                        foundFilesList.add(file);
                    } else if (file.isDirectory()) {
                        if (file.getName().equals("target") || file.getAbsolutePath().contains("\\test\\java")) {
                            continue;
                        }
                        List<File> subDirectoryFiles = searchFiles(file.getAbsolutePath(), targetFileNamePattern);
                        for (File subFile : subDirectoryFiles) {
                            foundFilesList.add(subFile);
                        }
                    }
                }
            }
        }
        return foundFilesList;
    }
}
