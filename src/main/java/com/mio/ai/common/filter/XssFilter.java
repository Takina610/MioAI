package com.mio.ai.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全局 XSS 过滤器：对所有请求参数进行危险 HTML 标签过滤
 *
 * 注意：此过滤器主要针对 @RequestParam / query string / form-data 参数。
 * 对于 @RequestBody JSON 参数，需要在 Controller 层结合 @Valid 和手动校验处理。
 */
@Component
@WebFilter(filterName = "xssFilter", urlPatterns = "/*")
@Order(1)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            // 静态资源放行
            String uri = httpRequest.getRequestURI();
            if (isStaticResource(uri)) {
                chain.doFilter(request, response);
                return;
            }
            chain.doFilter(new XssRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/static/")
                || uri.endsWith(".js")
                || uri.endsWith(".css")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".jpeg")
                || uri.endsWith(".gif")
                || uri.endsWith(".ico")
                || uri.endsWith(".svg")
                || uri.endsWith(".woff")
                || uri.endsWith(".woff2")
                || uri.endsWith(".ttf")
                || uri.endsWith(".eot");
    }
}
