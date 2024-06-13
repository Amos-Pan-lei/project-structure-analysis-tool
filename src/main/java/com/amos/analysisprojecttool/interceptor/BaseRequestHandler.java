package com.amos.analysisprojecttool.interceptor;

import cn.hutool.json.JSONUtil;
import com.amos.analysisprojecttool.bean.ComRes;
import com.amos.analysisprojecttool.config.AnalysisAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class BaseRequestHandler implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("请求 url = {}", request.getRequestURI());
        if (!AnalysisAutoConfiguration.isInited()) {
            log.error("项目还未启动完成");
            response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getWriter().append(JSONUtil.toJsonPrettyStr(ComRes.fail("项目还未启动完成，请稍后重试")));
            return false;
        }
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);

    }
}
