package com.amos.analysisprojecttool.service.sql;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class SqlXmlParseResultCache {

    @Value("${analysis.sqlXml.cache.dir:''}")
    private String CACHE_FILE_DIR = "cache";

    @Autowired
    SqlXmlParser sqlXmlParser;


    /**
     * 类名 对应 sqlxml解析结果map
     */
    @Getter
    Map<String, ParseXmlResult> xmlResultMap = new HashMap<>();

    /**
     * xml文件路径 对应 sqlxml解析结果map
     */
    @Getter
    Map<String, ParseXmlResult> cachedDocMap = new HashMap<>();

    public ParseXmlResult getParseXmlResult(File file) {
        return getParseXmlResult(file.getAbsolutePath());
    }

    /**
     * 获取解析结果
     */
    public ParseXmlResult getParseXmlResult(String xmlAbsolutePath) {
        ParseXmlResult parseXmlResult = cachedDocMap.get(xmlAbsolutePath);
        if (parseXmlResult != null) {
            return parseXmlResult;
        }
        //从文件缓存中获取 解析结果
        parseXmlResult = getParseXmlResultFromCacheFile(xmlAbsolutePath);
        if (parseXmlResult == null) {
            //文件缓存获取不到 则执行解析代码
            parseXmlResult = sqlXmlParser.parseXml(xmlAbsolutePath);
        }
        if (parseXmlResult != null) {
            //保存到本地缓存文件中去
            saveToFileCache(xmlAbsolutePath, parseXmlResult);
            //保存到内存缓存中去
            cachedDocMap.put(xmlAbsolutePath, parseXmlResult);
            xmlResultMap.put(parseXmlResult.getNameSpace(), parseXmlResult);
        }
        return parseXmlResult;
    }

    /**
     * 保存解析结果到本地缓存文件中去
     *
     * @param xmlAbsolutePath
     * @param parseXmlResult
     */
    private void saveToFileCache(String xmlAbsolutePath, ParseXmlResult parseXmlResult) {
        String storeFilePath = getStoreFilePath(xmlAbsolutePath);
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(parseXmlResult), storeFilePath);
    }

    private ParseXmlResult getParseXmlResultFromCacheFile(String xmlAbsolutePath) {
        String storeFilePath = getStoreFilePath(xmlAbsolutePath);
        ParseXmlResult parseXmlResult = cachedDocMap.get(xmlAbsolutePath);
        if (parseXmlResult != null) {
            return parseXmlResult;
        }

        boolean exist = FileUtil.exist(storeFilePath);
        if (exist) {
            String s = FileUtil.readUtf8String(storeFilePath);
            if (StrUtil.isNotBlank(s)) {
                parseXmlResult = JSONUtil.toBean(s, ParseXmlResult.class);
                if (Objects.nonNull(parseXmlResult)) {
                    cachedDocMap.put(xmlAbsolutePath, parseXmlResult);
                    xmlResultMap.put(parseXmlResult.getNameSpace(), parseXmlResult);

                }
            }
        }
        return parseXmlResult;
    }

    public String getStoreFilePath(String xmlAbsolutePath) {
        String forHashDir = StrUtil.removeSuffix(xmlAbsolutePath, FileUtil.getName(xmlAbsolutePath));
        String fileMd5 = calcFileMd5(xmlAbsolutePath);

        return CACHE_FILE_DIR + FileUtil.FILE_SEPARATOR + HashUtil.apHash(forHashDir) + FileUtil.FILE_SEPARATOR + FileUtil.mainName(xmlAbsolutePath)+"-"+fileMd5 + ".json";
    }

    public String calcFileMd5(String xmlAbsolutePath){
        File file = FileUtil.file(xmlAbsolutePath);
        // 计算文件的MD5值
        String md5 = DigestUtil.md5Hex(file);
        return md5;
    }

}
