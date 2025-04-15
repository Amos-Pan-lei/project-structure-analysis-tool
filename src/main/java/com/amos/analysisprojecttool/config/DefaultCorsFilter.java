package com.amos.analysisprojecttool.config;

import cn.hutool.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@ConditionalOnClass({Filter.class, ServletException.class})
@Component("corsFilter")
public class DefaultCorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        //跨域

        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        //跨域 Header
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "*");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        // 浏览器是会先发一次options请求，如果请求通过，则继续发送正式的post请求
        // 配置options的请求返回
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatus(HttpStatus.HTTP_OK);
            // hresp.setContentLength(0);
            response.getWriter().write("OPTIONS returns OK");
            return;
        }

        chain.doFilter(request, response);

    }
}
