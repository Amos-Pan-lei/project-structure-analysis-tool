package com.amos.analysisprojecttool.service.sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MapperFindResult {
    private List<String> sqlIds;
    private File mapperFile;
    private String mapperJavaName;
}
