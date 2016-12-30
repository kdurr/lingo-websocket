package lingo.client.multiplayer;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Configuration
public class MultiplayerConfig {

	@Bean
	public WebSocketStompClient stompClient(MessageConverter messageConverter) {
		WebSocketClient webSocketClient = new StandardWebSocketClient();
		WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
		stompClient.setMessageConverter(messageConverter);
		stompClient.setTaskScheduler(new ThreadPoolTaskScheduler());
		return stompClient;
	}

	@Bean
	public MessageConverter messageConverter() {
		List<MessageConverter> converters = new ArrayList<>();
		converters.add(new StringMessageConverter());
		converters.add(new ByteArrayMessageConverter());
		converters.add(new MappingJackson2MessageConverter());
		return new CompositeMessageConverter(converters);
	}

}
