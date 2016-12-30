package lingo.client.multiplayer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Component
public class StompTemplate {

	private static final Logger log = LoggerFactory.getLogger(StompTemplate.class);

	@Value("${server.host}")
	private String host;

	@Value("${server.port}")
	private int port;

	@Value("${server.stomp.endpoint}")
	private String stompEndpoint;

	@Autowired
	private ExecutorService executorService;

	@Autowired
	private WebSocketStompClient stompClient;

	private StompSession stompSession;

	private final BlockingQueue<SubscriptionRequest> subscriptionRequests = new LinkedBlockingQueue<>();

	public StompSession getSession() {
		/*
		 * TODO: If STOMP session is null or disconnected, create a new
		 * connection before returning this field.
		 */
		return stompSession;
	}

	@PostConstruct
	private void postConstruct() {
		final String url = String.format("ws://%s:%d/%s", host, port, stompEndpoint);
		executorService.execute(() -> stompClient.connect(url, new WebSocketSessionHandler()));
		new Thread(new WebSocketSessionListener()).start();
	}

	@PreDestroy
	private void preDestroy() {
		if (stompSession != null) {
			log.info("Disconnecting from STOMP endpoint...");
			stompSession.disconnect();
		}
		stompClient.stop();
	}

	public void subscribe(String destination, StompFrameHandler handler) {
		subscribe(destination, handler, null);
	}

	public void subscribe(String destination, StompFrameHandler handler, Consumer<Subscription> callback) {
		try {
			subscriptionRequests.put(new SubscriptionRequest(destination, handler, callback));
		} catch (InterruptedException e) {
			log.error("Failed to subscribe to destination: {}", destination, e);
		}
	}

	private class SubscriptionRequest {
		public final String destination;
		public final StompFrameHandler handler;
		public final Consumer<Subscription> callback;

		public SubscriptionRequest(String destination, StompFrameHandler handler, Consumer<Subscription> callback) {
			this.destination = destination;
			this.handler = handler;
			this.callback = callback;
		}

		public void onSubscribed(Subscription subscription) {
			if (callback != null) {
				callback.accept(subscription);
			}
		}
	}

	private class WebSocketSessionHandler extends StompSessionHandlerAdapter {
		@Override
		public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
			log.info("Connected to STOMP endpoint");
			stompSession = session;
		}
	}

	private class WebSocketSessionListener implements Runnable {

		@Override
		public void run() {
			while (true) {
				if (stompSession == null) {
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException ok) {
						ok.printStackTrace();
					}
					continue;
				}
				try {
					final SubscriptionRequest request = subscriptionRequests.take();
					final Subscription subscription = stompSession.subscribe(request.destination, request.handler);
					request.onSubscribed(subscription);
				} catch (InterruptedException e) {
					log.error("Failed to subscribe", e);
				}
			}
		}
	}

}
