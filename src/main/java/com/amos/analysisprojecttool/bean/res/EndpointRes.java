package com.amos.analysisprojecttool.bean.res;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Collection;

@ApiModel(description = "接口信息")
@Data
public class EndpointRes {

    /**
     * 接口地址
     */
    @ApiModelProperty("接口地址")
    private String uri;

    /**
     * 方法
     */
    @ApiModelProperty("方法")
    private Collection<MethodCallNodeInfo> methods;
}
