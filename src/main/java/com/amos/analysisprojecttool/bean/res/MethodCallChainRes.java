package com.amos.analysisprojecttool.bean.res;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallChain;
import com.amos.analysisprojecttool.bean.bytecode.MethodCallGraphNode;
import com.google.common.collect.Sets;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import sootup.java.core.AnnotationUsage;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        nodeInfo.setLeafNode(original.isLeafNode());
        Set<AnnotationUsage> methodAnnotations = original.getMethodAnnotations();
        if (original.isLeafNode() && CollUtil.isNotEmpty(methodAnnotations)) {
            Set<String> annotations = methodAnnotations.stream().map(x -> {
                try {
                    String anno = x.toString();
                    for (String excludeAnnotation : includeAnnotations) {
                        if (StrUtil.startWith(anno, excludeAnnotation)) {
                            return anno;
                        }
                    }
                } catch (Exception e) {
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());
            nodeInfo.setAnnotations(annotations);
        }
        return nodeInfo;
    }

    public static Set<String> includeAnnotations = Sets.newHashSet("@org.springframework.kafka.annotation",
            "@com.xxl.job.core.handler.annotation" );
}
