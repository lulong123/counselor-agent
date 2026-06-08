package com.counselor.agent.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class TeacherFilter implements Filter {

    public static final String TEACHER_ID_ATTR = "teacherId";
    private static final String HEADER = "X-Teacher-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String teacherId = req.getHeader(HEADER);
        if (teacherId == null || teacherId.isBlank()) {
            res.setStatus(400);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"缺少 X-Teacher-Id 请求头\"}");
            return;
        }

        req.setAttribute(TEACHER_ID_ATTR, teacherId.trim());
        chain.doFilter(request, response);
    }
}
