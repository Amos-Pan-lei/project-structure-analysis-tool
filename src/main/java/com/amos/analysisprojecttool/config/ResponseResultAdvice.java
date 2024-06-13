package com.amos.analysisprojecttool.config;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.amos.analysisprojecttool.bean.ComRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Slf4j
@ConditionalOnClass(ResponseBodyAdvice.class)
@ControllerAdvice(basePackages = "com.amos.saaswork")
public class ResponseResultAdvice implements ResponseBodyAdvice<Object> {


    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    private boolean isCollection(Class<?> clz) {
        if (clz.equals(Collection.class)) {
            return true;
        }

        Class<?>[] interfaces = clz.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (isCollection(anInterface)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        ComRes res;
        if (body instanceof ComRes) {
            res = (ComRes) body;
            log.info("接口 {} 响应 {}", request.getURI(), JSONUtil.toJsonPrettyStr(res));
            return res;
        } else {
            if (Objects.nonNull(body)) {
                res = ComRes.success(body);
            } else {
                Class<?> returnParameterType = returnType.getParameterType();
                if (returnParameterType.equals(void.class) || returnParameterType.isPrimitive()) {
                    res = ComRes.success();
                } else if (returnParameterType.equals(String.class)) {
                    res = ComRes.success(StrUtil.EMPTY);
                } else if (returnParameterType.isArray()) {
                    res = ComRes.success(ListUtil.empty());
                } else if (isCollection(returnParameterType)) {
                    res = ComRes.success(Collections.emptyList());
                } else {
                    res = ComRes.success();
                }
            }
        }
        log.info("接口 {} 响应 {}", request.getURI(), JSONUtil.toJsonPrettyStr(res));
        return res;
    }
}
