package com.yupi.yupicturebackend.config;

import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.manager.auth.StpKit;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 给 Python Agent 的“内部鉴权”入口：
 * - Agent 通过请求头 X-Internal-Token 调用 Java 后端
 * - Java 端在服务内把该请求视为管理员登录（同时满足 HttpSession + Sa-Token SPACE 权限校验）
 *
 * 优点：不需要在 Agent 中保存管理员账号密码，也不需要 Python 去“读 Redis Session”。
 *
 * 安全建议：
 * - token 放在环境变量或配置中心
 * - 默认仅允许本机访问（127.0.0.1 / ::1），如需远程再扩展白名单
 */
@Slf4j
@Component
public class AgentInternalAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    /**
     * 这些接口由 Python Agent 内部调用；当 internal token 匹配时，不做 IP 校验。
     * 目的：避免 Docker/远程调用时 remoteAddr 不是 127.x 导致被拦截。
     */
    private static final String[] INTERNAL_NO_IP_CHECK_PATHS = new String[] {
            "/picture/get/vo",
            "/picture/list/page/vo",
            "/video/agent/persist"
    };

    @Value("${agent.internalToken:}")
    private String internalToken;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * 可选：指定管理员 userId；不配置则自动找一个 admin 账号
     */
    @Value("${agent.adminUserId:}")
    private Long adminUserId;

    @Resource
    private UserService userService;

    /**
     * 缓存一个 token，避免每个请求都创建新 token（token 由 Sa-Token 管理）
     */
    private volatile String cachedSaTokenValue;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = request.getHeader(HEADER_INTERNAL_TOKEN);
        boolean tokenConfigured = internalToken != null && !internalToken.trim().isEmpty();
        if (tokenConfigured) {
            if (token == null || !internalToken.equals(token)) {
                filterChain.doFilter(request, response);
                return;
            }
            log.info("Agent internal token matched, uri={}, remoteAddr={}", request.getRequestURI(), request.getRemoteAddr());
        } else {
            // 兜底：本地开发（spring.profiles.active=local）若未配置 internalToken，
            // 但请求显式携带了 X-Internal-Token，则放行（仅限本机）。
            if (!"local".equalsIgnoreCase(activeProfile) || token == null || token.trim().isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("Agent internalToken 未配置，但当前为 local 环境，启用 X-Internal-Token 兜底放行，uri={}", request.getRequestURI());
        }
        // 默认仅允许本机调用，避免 token 泄露带来风险
        String uri = request.getRequestURI();
        if (!isNoIpCheckPath(uri)) {
            String remoteAddr = request.getRemoteAddr();
            if (!(remoteAddr.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr))) {
                log.warn("Agent internal token used from non-local address: {}", remoteAddr);
                filterChain.doFilter(request, response);
                return;
            }
        }


        // 如果已经有登录态（session 里已有 user_login），直接放行
        Object loginState = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (loginState != null) {
            filterChain.doFilter(request, response);
            return;
        }

        User adminUser = getAdminUser();
        if (adminUser == null) {
            log.warn("Agent internal auth enabled but no admin user found, skip.");
            filterChain.doFilter(request, response);
            return;
        }

        // 1) 写入 HttpSession（兼容 getLoginUser() 的 session 校验）
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, adminUser);

        // 2) 满足 Sa-Token SPACE 权限校验：给当前请求补齐 token header
        String tokenName = StpKit.SPACE.getTokenName();
        String tokenValue = cachedSaTokenValue;
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            // 首次创建并缓存 token（会写入 Sa-Token 的 token-session）
            StpKit.SPACE.login(adminUser.getId());
            tokenValue = StpKit.SPACE.getTokenValue();
            cachedSaTokenValue = tokenValue;
            // 同步写入 Sa-Token session，便于权限扩展里读取 USER_LOGIN_STATE
            StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, adminUser);
        }

        final String finalTokenValue = tokenValue;
        HttpServletRequest wrapped = new HeaderInjectRequestWrapper(request, Collections.singletonMap(tokenName, finalTokenValue));
        filterChain.doFilter(wrapped, response);
    }

    private boolean isNoIpCheckPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        for (String p : INTERNAL_NO_IP_CHECK_PATHS) {
            if (uri.endsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private User getAdminUser() {
        if (adminUserId != null && adminUserId > 0) {
            User byId = userService.getById(adminUserId);
            if (byId != null) {
                return byId;
            }
        }
        return userService.lambdaQuery()
                .eq(User::getUserRole, UserRoleEnum.ADMIN.getValue())
                .last("limit 1")
                .one();
    }

    /**
     * 注入请求头（让 Sa-Token 的拦截器能在同一请求里读取到 token）
     */
    private static class HeaderInjectRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        HeaderInjectRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = new LinkedHashMap<>(extraHeaders);
        }

        @Override
        public String getHeader(String name) {
            String v = extraHeaders.get(name);
            if (v != null) {
                return v;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = extraHeaders.get(name);
            if (v != null) {
                return Collections.enumeration(Collections.singletonList(v));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Map<String, String> map = new LinkedHashMap<>(extraHeaders);
            Enumeration<String> base = super.getHeaderNames();
            while (base.hasMoreElements()) {
                String n = base.nextElement();
                if (!map.containsKey(n)) {
                    map.put(n, super.getHeader(n));
                }
            }
            return Collections.enumeration(map.keySet());
        }
    }
}


