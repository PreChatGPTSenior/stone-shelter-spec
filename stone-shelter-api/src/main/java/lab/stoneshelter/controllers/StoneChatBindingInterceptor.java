package lab.stoneshelter.controllers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lab.stoneshelter.services.StoneChatAdmissionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class StoneChatBindingInterceptor implements HandlerInterceptor {
    public static final String SESSION = "stoneChatSessionId";
    private final StoneChatAdmissionService admission;
    public StoneChatBindingInterceptor(StoneChatAdmissionService admission) { this.admission = admission; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean send = "POST".equals(request.getMethod());
        admission.prepare(send, request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), request.getHeader("Origin"));
        String cookie = null;
        if (request.getCookies() != null) for (Cookie candidate : request.getCookies())
            if ("stone_chat_session".equals(candidate.getName())) { cookie = candidate.getValue(); break; }
        request.setAttribute(SESSION, admission.bind(cookie, sessionId -> { if (!request.getRequestURI().equals("/api/v1/chat/messages/stream")) writeCookie(response, request, sessionId, admission.retentionSeconds()); }));
        return true;
    }
    public static void writeCookie(HttpServletResponse response, HttpServletRequest request, String sessionId, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("stone_chat_session", sessionId)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/api/v1/chat").maxAge(seconds).build().toString());
    }
}
