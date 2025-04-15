package com.amos.analysisprojecttool.bean.bytecode;

import lombok.Data;
import sootup.java.core.AnnotationUsage;
import sootup.java.core.JavaSootMethod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@Data
public class MethodCallGraphNode {
    public MethodCallGraphNode() {
    }

    public MethodCallGraphNode(JavaSootMethod current) {
        this.current = current;
    }

    private JavaSootMethod current;
    private String endPointUrl;
    private Set<AnnotationUsage> methodAnnotations;
    private boolean leafNode = false;
    private Collection<MethodCallGraphNode> childs = new HashSet<>();

    /**
     * 遍历访问所有子节点
     *
     * @param visitor
     */
    public void visit(Consumer<MethodCallGraphNode> visitor) {
        visitor.accept(this);
        for (MethodCallGraphNode child : childs) {
            child.visit(visitor);
        }
    }

}
