package com.amos.analysisprojecttool.bean.res;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MethodAndEndPointResult {
    private String methodSignature;
    private String endPointUrl;
    private String moduleName;
    private String tableName;
}