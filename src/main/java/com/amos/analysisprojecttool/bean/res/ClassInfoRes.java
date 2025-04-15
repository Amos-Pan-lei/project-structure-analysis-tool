package com.amos.analysisprojecttool.bean.res;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Collection;

@ApiModel(description = "类信息")
@Data
public class ClassInfoRes {

    /**
     * 类全限定名
     */
    @ApiModelProperty("类全限定名")
    private String classFullName;

    /**
     * 方法
     */
    @ApiModelProperty("方法")
    private Collection<MethodCallNodeInfo> methods;
}
