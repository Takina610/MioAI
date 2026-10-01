package com.mio.ai.common.filter;

import com.mio.ai.common.utils.XssUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * XSS 请求包装器：对请求参数进行安全过滤
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return XssUtils.cleanRichText(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = XssUtils.cleanRichText(values[i]);
        }
        return cleaned;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> paramMap = super.getParameterMap();
        return paramMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String[] values = entry.getValue();
                            if (values == null) return null;
                            String[] cleaned = new String[values.length];
                            for (int i = 0; i < values.length; i++) {
                                cleaned[i] = XssUtils.cleanRichText(values[i]);
                            }
                            return cleaned;
                        }
                ));
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return XssUtils.cleanRichText(value);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Enumeration<String> headers = super.getHeaders(name);
        return new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return headers.hasMoreElements();
            }

            @Override
            public String nextElement() {
                return XssUtils.cleanRichText(headers.nextElement());
            }
        };
    }
}
