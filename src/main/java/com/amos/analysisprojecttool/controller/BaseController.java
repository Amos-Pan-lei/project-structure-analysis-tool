package com.amos.analysisprojecttool.controller;

import cn.hutool.core.util.ZipUtil;
import com.amos.analysisprojecttool.bean.ComRes;
import com.amos.analysisprojecttool.bean.req.CallGraphReq;
import com.amos.analysisprojecttool.bean.res.ClassInfoRes;
import com.amos.analysisprojecttool.bean.res.EndpointRes;
import com.amos.analysisprojecttool.bean.res.MethodAndEndPointResult;
import com.amos.analysisprojecttool.bean.res.MethodCallChainRes;
import com.amos.analysisprojecttool.service.AnalysisTool;
import com.amos.analysisprojecttool.util.CallGraphUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@Api(tags = "基本接口")
public class BaseController {

    @Autowired
    private AnalysisTool analysisTool;

    /**
     * 查询某个方法的调用图-json
     *
     * @param req
     * @return
     */
    @ApiOperation("查询某个方法的调用图-json")
    @PostMapping("/callGraph")
    public MethodCallChainRes callGraph(@RequestBody CallGraphReq req) {
        if (req.getDirection().equals("down")) {
            return analysisTool.callGraph(req.getClassFullyName(), req.getMethodName());
        } else {
            return analysisTool.callGraphReverse(req.getClassFullyName(), req.getMethodName());
        }
    }


    /**
     * 查询某个表涉及到的接口-json
     *
     * @param req
     * @return
     */
    @ApiOperation("查询某个表涉及到的接口-json")
    @PostMapping("/oneTableMappingEndPoints")
    public ComRes<Set<MethodAndEndPointResult>> oneTableMappingEndPoints(@RequestBody CallGraphReq req) {
        return ComRes.success(analysisTool.oneTableMappingEndPoints(req.getTableName()));
    }


    /**
     * 查询某个方法的调用图 - 以 mermaid文本的形式返回
     *
     * @param req
     * @return
     */
    @ApiOperation("查询某个方法的调用图 - 以 mermaid文本的形式返回")
    @PostMapping("/callGraphToMermaidText")
    public ComRes<String> callGraphToMermaidText(@RequestBody CallGraphReq req) {

        if (req.getDirection().equals("down")) {
            MethodCallChainRes methodCallChainRes = analysisTool.callGraph(req.getClassFullyName(), req.getMethodName());
            return ComRes.success(CallGraphUtils.graphChainToMermaidText(methodCallChainRes));
        } else {
            MethodCallChainRes methodCallChainRes = analysisTool.callGraphReverse(req.getClassFullyName(), req.getMethodName());
            return ComRes.success(CallGraphUtils.graphChainToMermaidText(methodCallChainRes));
        }
    }

    /**
     * 查询某个方法的调用图  md 内容
     *
     * @param req
     * @return
     */
    @ApiOperation("查询某个方法的调用图  md 内容")
    @PostMapping("/queryCallGraphToMdTxt")
    public ComRes<String> queryCallGraphToMdTxt(@RequestBody CallGraphReq req) {
        String content = analysisTool.queryCallGraphToMdTxt(req.getDirection(), req.getClassFullyName(), req.getMethodName(), req.isGenMermaidText());
        // 构建响应实体
        return ComRes.success(content);
    }

    /**
     * 查询指定表名 对于的函数调用图谱，以md文档格式下载
     *
     * @param req
     * @return
     */
    @ApiOperation("查询指定表名 对于的函数调用图谱，以md文档格式下载")
    @PostMapping("/queryTableMapperCallGraphToMd")
    public ResponseEntity<ByteArrayResource> queryTableMapperCallGraphToMd(@RequestBody CallGraphReq req) {
        String content = analysisTool.queryTableMapperCallGraphToMd(req.getTableName(), req.isGenMermaidText());
        // 将文本内容转换为字节数组流
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(contentBytes);
        // 设置响应头，包括文件名和内容类型
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + req.getTableName() + ".md");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        // 构建响应实体
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 查询指定表名 对于的函数调用图谱，md内容
     *
     * @param req
     * @return
     */
    @ApiOperation("查询指定表名 对于的函数调用图谱，md内容 ")
    @PostMapping("/queryTableMapperCallGraphToMdTxt")
    public ComRes<String> queryTableMapperCallGraphToMdTxt(@RequestBody CallGraphReq req) {
        String content = analysisTool.queryTableMapperCallGraphToMd(req.getTableName(), req.isGenMermaidText());
        return ComRes.success(content);
    }

    /**
     * 暂时无效
     *
     * @param req
     * @return
     * @throws Exception
     */
    @ApiOperation("查询多个表名 对于的函数调用图谱，以zip格式下载")
    @PostMapping("/queryTableMapperCallGraphBatchToMd")
    public ResponseEntity<ByteArrayResource> queryTableMapperCallGraphBatchToMd(@RequestBody CallGraphReq req) throws
            Exception {
        Map<String, String> resultMap = analysisTool.queryTableMapperCallGraphBatchToMd(req.getTableNameList(), req.isGenMermaidText());
        try {
            // 创建一个内存中的字节数组输出流来存储 ZIP 文件
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
            ArrayList<String> fileNames = new ArrayList<>();
            ArrayList<InputStream> inputStreams = new ArrayList<>();
            resultMap.forEach((tableName, content) -> {
                fileNames.add(tableName + ".md");
                inputStreams.add(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            });
            // 添加多个文本文件到 ZIP 压缩包
            ZipUtil.zip(zipOutputStream, fileNames.toArray(new String[]{}), inputStreams.toArray(new InputStream[]{}));
            // 构建 ZIP 文件的字节数组资源
            byte[] zipBytes = byteArrayOutputStream.toByteArray();
            ByteArrayResource resource = new ByteArrayResource(zipBytes);
            zipOutputStream.close();

            // 设置响应头，包括文件名和内容类型
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            String outputFilename = "multiply.zip";
            headers.setContentDispositionFormData(outputFilename, outputFilename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            // 构建响应实体
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            log.error("压缩zip文件报错", e);
            return (ResponseEntity<ByteArrayResource>) ResponseEntity.noContent();
        }

    }

    /**
     * 刷新table 对应的接口的笔记
     */
    @ApiOperation("刷新table 对应的接口的笔记")
    @PostMapping("/flushTableMappingMethodsNote")
    public void flushTableMappingMethodsNote(@RequestBody CallGraphReq req) throws
            Exception {
        analysisTool.flushTableMappingMethodsNote(req.getTableNameList());
    }



    /**
     * 所有接口
     *
     * @return
     */
    @ApiOperation("所有接口")
    @GetMapping("/allEndPoints")
    public Collection<EndpointRes> allEndPoints() {
        return analysisTool.allEndPoints();
    }

    /**
     * 所有表
     *
     * @return
     */
    @ApiOperation("扫描所有表对应的接口")
    @GetMapping("/allTables")
    public ComRes<Collection<String>> allTables() {
        return ComRes.success(analysisTool.allTables());
    }

    /**
     * 查询所有的类和方法
     *
     * @param req
     * @return
     */
    @ApiOperation("查询所有的类和方法")
    @PostMapping("/allClassInfo")
    public ComRes<List<ClassInfoRes>> allClassInfo(@RequestBody CallGraphReq req) {
        return ComRes.success(analysisTool.allClassInfo(req.getClassFullyName(), req.getMethodName()));
    }
}
