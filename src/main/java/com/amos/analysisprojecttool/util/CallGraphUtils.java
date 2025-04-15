package com.amos.analysisprojecttool.util;

import cn.hutool.core.util.StrUtil;
import com.amos.analysisprojecttool.bean.res.MethodCallChainRes;
import com.amos.analysisprojecttool.bean.res.MethodCallGraphNodeRes;
import com.amos.analysisprojecttool.bean.res.MethodCallNodeInfo;
import com.google.common.collect.HashMultimap;

import java.util.*;
import java.util.function.BiConsumer;

public class CallGraphUtils {

    public static String graphChainToMermaidText(MethodCallChainRes methodCallChainRes) {
        StringBuilder stringBuilder = new StringBuilder("flowchart TD\n");
        Set<MethodCallNodeInfo> nodeInfoSet = new HashSet<>();
        Map<MethodCallNodeInfo, String> nodeNameMap = new HashMap<>();
        HashMultimap<MethodCallNodeInfo, MethodCallNodeInfo> callRelation = HashMultimap.create();

        methodCallChainRes.getHead().visit(nodeRes -> {
            nodeInfoSet.add(nodeRes.getCurrent());
            for (MethodCallGraphNodeRes child : nodeRes.getChilds()) {
                callRelation.put(nodeRes.getCurrent(), child.getCurrent());
            }
        });
        for (MethodCallNodeInfo nodeInfo : nodeInfoSet) {
            String nodeName = UUID.randomUUID().toString().replace("-", "");
            nodeNameMap.put(nodeInfo, nodeName);
            String nodeRemark = nodeInfo.getNodeRemark();
            nodeRemark = StrUtil.replace(nodeRemark,"\"","'");
            nodeRemark = StrUtil.replace(nodeRemark,"[","'");
            nodeRemark = StrUtil.replace(nodeRemark,"]","'");
            stringBuilder.append(String.format("%s[ %s ]\n", nodeName, nodeRemark));
        }
        callRelation.forEach((parent, child) -> {
            stringBuilder.append(String.format("%s --> %s \n", nodeNameMap.get(parent), nodeNameMap.get(child)));
//            if (StrUtil.isNotEmpty(child.getSqlContent())) {
//                String nodeRemark = "*************sql*************\n" + child.getSqlContent();
//                stringBuilder.append(String.format("%s --> %s[\"`%s`\"] \n", nodeNameMap.get(child), UUID.randomUUID().toString().replace("-", ""), nodeRemark.replaceAll("`", "")));
//            }
        });
        return stringBuilder.toString();
    }

    /**
     * 逐层遍历 树
     *
     * @param node
     * @param consumer
     */
    public static void traverseTree(MethodCallGraphNodeRes node, BiConsumer<MethodCallGraphNodeRes, Integer> consumer) {
        traverseTree(node, 1, consumer);
    }

    private static void traverseTree(MethodCallGraphNodeRes node, int depth, BiConsumer<MethodCallGraphNodeRes, Integer> consumer) {
        if (node == null) {
            return;
        }
        // 打印节点信息，可以根据需要修改操作
        consumer.accept(node, depth);
        // 递归遍历子节点
        for (MethodCallGraphNodeRes child : node.getChilds()) {
            traverseTree(child, depth + 1, consumer);
        }
    }


}
