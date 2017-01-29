package lingo.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lingo.common.Player;

@Component
public class SessionManager implements ApplicationListener<AbstractSubProtocolEvent> {

	public interface Listener {
		void playerJoined(Player player);
		void playerLeft(Player player);
	}

	private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

	private final Map<String, Player> playerBySession = new HashMap<>();

	private final Set<Listener> listeners = new HashSet<>();

	public void addListener(Listener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	public Player getPlayer(String sessionId) {
		return playerBySession.get(sessionId);
	}

	public int getPlayerCount() {
		return playerBySession.size();
	}

	@Override
	public void onApplicationEvent(AbstractSubProtocolEvent event) {
		if (event instanceof SessionConnectedEvent) {
			onSessionConnected((SessionConnectedEvent) event);
		} else if (event instanceof SessionDisconnectEvent) {
			onSessionDisconnect((SessionDisconnectEvent) event);
		}
	}

	private void onSessionConnected(SessionConnectedEvent event) {
		final String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
		final Player player = new Player(sessionId);
		log.info("Player connected: {}", player);
		playerBySession.put(sessionId, player);
		synchronized (listeners) {
			for (Listener listener : listeners) {
				listener.playerJoined(player);
			}
		}
	}

	private void onSessionDisconnect(SessionDisconnectEvent event) {
		final String sessionId = event.getSessionId();
		final Player player = playerBySession.remove(sessionId);
		log.info("Player disconnected: {}", player);
		synchronized (listeners) {
			for (Listener listener : listeners) {
				listener.playerLeft(player);
			}
		}
	}

	public void removeListener(Listener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

}
