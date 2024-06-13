package com.amos.analysisprojecttool.bean.res;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * 方法函数调用图-方法节点信息
 */
@Data
@ApiModel(description = "方法函数调用图-方法节点信息")
public class MethodCallGraphNodeRes {
    public MethodCallGraphNodeRes() {
    }

    public MethodCallGraphNodeRes(MethodCallNodeInfo current) {
        this.current = current;
    }

    /**
     * 当前节点信息
     */
    @ApiModelProperty("当前节点信息")
    private MethodCallNodeInfo current;
    /**
     * 子节点
     */
    @ApiModelProperty("子节点")
    private Collection<MethodCallGraphNodeRes> childs = new HashSet<>();


    public void visit(Consumer<MethodCallGraphNodeRes> visitor) {
        visitor.accept(this);
        for (MethodCallGraphNodeRes child : childs) {
            child.visit(visitor);
        }
    }
}
