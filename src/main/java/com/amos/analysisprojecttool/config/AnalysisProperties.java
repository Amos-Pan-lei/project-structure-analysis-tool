package com.amos.analysisprojecttool.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
@Configuration
@ConfigurationProperties(prefix = "analysis")
public class AnalysisProperties {
    /**
     * 解析的类包名前缀
     */
    private String typePackageNamePrefix = "";
    /**
     * 解析方法体中 执行语句的调用对象的 类名前缀
     */
    private String methodStmtsValInvokerPackageNamePrefix = "";
    /**
     * 输入 目标 字节码目录[]
     */
    private List<String> targetDirs = new ArrayList<>();
    /**
     * 基础 jar 包目录
     */
    private List<String> jarLibs = new ArrayList<>();
    private List<String> sqlXmlBaseDirs = new ArrayList<>();


}
