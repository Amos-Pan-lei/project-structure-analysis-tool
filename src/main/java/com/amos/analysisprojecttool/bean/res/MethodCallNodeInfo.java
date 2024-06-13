package com.amos.analysisprojecttool.bean.res;

import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import sootup.core.model.SootMethod;

/**
 * 调用图 节点详细信息
 */
@ApiModel(description = "调用图 节点详细信息")
@Data
public class MethodCallNodeInfo {
    /**
     * 方法签名
     */
    @ApiModelProperty("方法签名")
    private String methodSignature;
    /**
     * 类全限定名
     */
    @ApiModelProperty("类全限定名")
    private String classFullName;
    /**
     * 方法名
     */
    @ApiModelProperty("方法名")
    private String methodName;
    /**
     * sql内容
     */
    @ApiModelProperty("sql内容")
    private String sqlContent;
    /**
     * web接口 url
     */
    @ApiModelProperty("web接口 url")
    private String endPointUrl;

    public MethodCallNodeInfo() {
    }

    public MethodCallNodeInfo(SootMethod sootMethod) {
        this.methodSignature = sootMethod.getSignature().getSubSignature().toString();
        this.classFullName = sootMethod.getDeclaringClassType().getFullyQualifiedName();
        this.methodName = sootMethod.getName();
    }

    public String getNodeRemark() {
        String nodeRemark = getClassFullName() + "#" + getMethodName();
        if (StrUtil.isNotEmpty(getEndPointUrl())) {
            nodeRemark = nodeRemark + "\nURL : [ " + getEndPointUrl() + " ]";
        }

        return nodeRemark;
    }
}
