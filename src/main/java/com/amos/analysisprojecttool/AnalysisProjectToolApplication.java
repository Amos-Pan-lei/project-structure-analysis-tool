package com.amos.analysisprojecttool;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.amos.analysisprojecttool.database.mapper")
@SpringBootApplication
public class AnalysisProjectToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysisProjectToolApplication.class, args);
    }

}
