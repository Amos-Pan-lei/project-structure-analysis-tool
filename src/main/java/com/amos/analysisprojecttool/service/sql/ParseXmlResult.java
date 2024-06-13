package com.amos.analysisprojecttool.service.sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParseXmlResult {
    private String nameSpace;
    //        private List<Element> elements;
//        private List<Element> sqlElements;
    private Map<String, String> elementSqlTextMap;
    private Map<String, Collection<String>> elementTablesMap;
}