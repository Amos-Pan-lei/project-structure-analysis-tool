package com.amos.analysisprojecttool.bean.bytecode;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Data
public class MethodCallChain {

    private MethodCallGraphNode head;

    /**
     * 遍历访问所有子节点
     *
     * @param visitor
     */
    public void visit(Consumer<MethodCallGraphNode> visitor) {
        head.visit(visitor);
    }

    public int length() {
        if (head == null) return 0;
        AtomicInteger count = new AtomicInteger();
        visit(node -> {
            count.set(count.get() + 1);
        });
        return count.get();
    }
}
