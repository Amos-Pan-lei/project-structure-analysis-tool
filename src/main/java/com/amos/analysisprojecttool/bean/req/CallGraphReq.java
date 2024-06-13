package com.amos.analysisprojecttool.bean.req;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
public class CallGraphReq {
    private String direction = "down";
    private String classFullyName;
    private String methodName;

    private String tableName;
    private boolean genMermaidText;
    private List<String> tableNameList;
}
