package com.amos.analysisprojecttool.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化方法
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // 生成traceId并设置到MDC中
            String traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);

            // 继续处理请求
            chain.doFilter(request, response);
        } finally {
            // 清除MDC中的traceId
            MDC.remove("traceId");
        }
    }

    @Override
    public void destroy() {
        // 销毁方法
    }
}