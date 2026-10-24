package lab.stoneshelter.controllers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.ServletException;
import lab.stoneshelter.handlers.StoneChatStreamReturnValueHandler;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class StoneChatStreamWebConfiguration implements WebMvcConfigurer {
    private final StoneChatStreamReturnValueHandler handler;
    public StoneChatStreamWebConfiguration(StoneChatStreamReturnValueHandler handler) { this.handler = handler; }
    @Override public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) { handlers.add(handler); }
    @Bean public WebServerFactoryCustomizer<TomcatServletWebServerFactory> stoneChatStreamNativeCloser() {
        return factory -> factory.addContextValves(new ValveBase(true) {
            @Override public void invoke(Request request, Response response) throws IOException, ServletException {
                if (request.getRequestURI().equals("/api/v1/chat/messages/stream")) {
                    UUID token = UUID.randomUUID(); request.setAttribute(StoneChatStreamReturnValueHandler.REQUEST_TOKEN, token);
                    request.setAttribute(StoneChatStreamReturnValueHandler.ABORT, (Runnable) () -> {
                        if (token.equals(request.getAttribute(StoneChatStreamReturnValueHandler.REQUEST_TOKEN)))
                            response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, new IOException("Chat stream delivery ended"));
                    });
                }
                getNext().invoke(request, response);
            }
        });
    }
}
