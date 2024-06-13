package com.amos.analysisprojecttool.util;

import org.objectweb.asm.tree.AnnotationNode;
import sootup.java.bytecode.frontend.AsmUtil;
import sootup.java.core.AnnotationUsage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MyAsmUtil {
    private MyAsmUtil() {
    }

    public static List<AnnotationUsage> convertAnnotation(List<AnnotationNode> nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        return StreamSupport.stream(AsmUtil.createAnnotationUsage(nodes).spliterator(), false)
                .collect(Collectors.toList());
    }


}
