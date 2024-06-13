package com.amos.analysisprojecttool.util;

import cn.hutool.core.util.StrUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlAnalysisUtils {
    private SqlAnalysisUtils() {
    }

    public static boolean sqlContainsTable(String sqlContext, String tableName) {
        if (StrUtil.isBlank(sqlContext) || StrUtil.isBlank(tableName)) {
            return false;
        }
        // 使用正则表达式匹配表名
        String regex = "\\b" + Pattern.quote(tableName) + "\\b";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sqlContext);
        return matcher.find();
    }

    /**
     * 匹配表名的正则表达式
     */

    static Pattern findTablesPattern = Pattern.compile("\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    /**
     * 匹配 on duplicated key update 语句正则
     */
    static Pattern excludePattern = Pattern.compile("(?i)\\bON\\s+DUPLICATE\\s+KEY\\s+UPDATE[\\s\\S]*$", Pattern.CASE_INSENSITIVE);

    /**
     * 从sql文本中找出 所有的表名
     *
     * @param sqlText
     */
    public static Set<String> parseTablesFromText(String sqlText) {
        if (StrUtil.isBlank(sqlText)) {
            return Collections.emptySet();
        }
        Matcher excludeMatcher = excludePattern.matcher(sqlText);
        if (excludeMatcher.find()) {
            sqlText = sqlText.substring(0, excludeMatcher.start());
        }
        Matcher matcher = findTablesPattern.matcher(sqlText);
        Set<String> tableNames = new HashSet<>();
        while (matcher.find()) {
            String tableName = matcher.group(1);
            tableName = StrUtil.removeSuffix(StrUtil.removePrefix(tableName, "`"), "`");
            tableNames.add(tableName);
        }
        return tableNames;

    }
}
