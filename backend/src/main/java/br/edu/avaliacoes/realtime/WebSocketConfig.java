package br.edu.avaliacoes.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;
    private final String origin;

    public WebSocketConfig(JwtDecoder decoder, JwtAuthenticationConverter converter, @Value("${app.origin}") String origin) {
        this.decoder = decoder;
        this.converter = converter;
        this.origin = origin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origin);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                
                var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (h == null || h.getCommand() == null) return message;
                
                if (StompCommand.CONNECT.equals(h.getCommand())) {
                    String token = h.getFirstNativeHeader("Authorization");
                    if (token == null || !token.startsWith("Bearer "))
                        throw new AccessDeniedException("Token obrigatório");
                    var jwt = decoder.decode(token.substring(7));
                    if (!java.util.List.of("ADMIN", "PROFESSOR").contains(jwt.getClaimAsString("role")))
                        throw new AccessDeniedException("Perfil inválido");
                    h.setUser(converter.convert(jwt));
                } else if (StompCommand.SUBSCRIBE.equals(h.getCommand())) {
                    if (h.getUser() == null || !"/user/queue/monitor".equals(h.getDestination()))
                        throw new AccessDeniedException("Assinatura não permitida");
                } else if (StompCommand.SEND.equals(h.getCommand())) throw new AccessDeniedException("Use a API");
                return message;
            }
        });
    }
}

