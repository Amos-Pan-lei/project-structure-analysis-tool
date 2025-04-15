package com.amos.analysisprojecttool.config;

import com.amos.analysisprojecttool.service.SootUpByteCodeAnalysis;
import com.amos.analysisprojecttool.service.sql.SqlXmlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@AutoConfigureAfter(AnalysisProperties.class)
@Slf4j
public class AnalysisAutoConfiguration implements ApplicationListener<ApplicationStartedEvent> {
    static boolean INITD = false;

    public static boolean isInited() {
        return AnalysisAutoConfiguration.INITD;
    }

    @Autowired
    private AnalysisProperties analysisProperties;

    @Autowired
    SqlXmlService sqlXmlService;

    SootUpByteCodeAnalysis sootUpByteCodeAnalysis;


    @Bean
    public SootUpByteCodeAnalysis sootUpByteCodeAnalysis() {
        SootUpByteCodeAnalysis sootUpByteCodeAnalysis = new SootUpByteCodeAnalysis(analysisProperties.getTargetDirs(), analysisProperties.getJarLibs());
        this.sootUpByteCodeAnalysis = sootUpByteCodeAnalysis;
        return sootUpByteCodeAnalysis;
    }


    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        AnalysisAutoConfiguration.INITD = false;
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> {
            sootUpByteCodeAnalysis.startUp();
        }, executorService);

        CompletableFuture<Void> voidCompletableFuture1 = CompletableFuture.runAsync(() -> {
            for (String sqlXmlBaseDir : analysisProperties.getSqlXmlBaseDirs()) {
                sqlXmlService.parseDirXml(sqlXmlBaseDir);
            }
        }, executorService);
        voidCompletableFuture.join();
        voidCompletableFuture1.join();
        executorService.shutdown();
        AnalysisAutoConfiguration.INITD = true;
        log.info("analysis project tool 初始化完毕");
    }
}

