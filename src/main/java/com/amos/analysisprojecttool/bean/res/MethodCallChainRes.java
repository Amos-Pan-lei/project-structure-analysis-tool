package com.amos.analysisprojecttool.bean.res;

import com.amos.analysisprojecttool.bean.bytecode.MethodCallChain;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallGraphNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Objects;

/**
 * 方法调用函数图响应
 */
@Data
@ApiModel(description = "方法调用函数图响应")
public class MethodCallChainRes {
    /**
     * 方法函数调用图 根节点
     */
    @ApiModelProperty("方法函数调用图 根节点")
    private MethodCallGraphNodeRes head;

    public boolean isEmpty() {
        return head == null;
    }

    public static MethodCallChainRes copyMethodCallChain(MethodCallChain original) {
        if (original == null) {
            return null;
        }

        // 创建一个新的 MethodCallChain
        MethodCallChainRes copy = new MethodCallChainRes();

        // 递归复制树状结构
        copy.head = copyMethodCallGraphNode(original.getHead());

        return copy;
    }

    private static MethodCallGraphNodeRes copyMethodCallGraphNode(MethodCallGraphNode original) {
        if (original == null) {
            return null;
        }

        // 创建一个新的 MethodCallGraphNode，这里可以根据需要进行属性转换
        MethodCallGraphNodeRes copyNode = new MethodCallGraphNodeRes(transformNode(original));
        // 递归复制子节点
        for (MethodCallGraphNode child : original.getChilds()) {
            MethodCallGraphNodeRes copyChild = copyMethodCallGraphNode(child);
            copyNode.getChilds().add(copyChild);
        }
        return copyNode;
    }

    private static MethodCallNodeInfo transformNode(MethodCallGraphNode original) {
        if (Objects.isNull(original)) {
            return null;
        }
        MethodCallNodeInfo nodeInfo = new MethodCallNodeInfo(original.getCurrent());
        nodeInfo.setEndPointUrl(original.getEndPointUrl());
        return nodeInfo;
    }
}
