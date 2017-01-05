package lingo.server;

import static org.springframework.messaging.simp.SimpMessageHeaderAccessor.SESSION_ID_HEADER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lingo.client.api.StompTopics;
import lingo.common.ChatMessage;
import lingo.common.Game;
import lingo.common.Report;

@Controller
@MessageMapping("/lingo")
public class LingoController implements ApplicationListener<AbstractSubProtocolEvent> {

	private static final Logger log = LoggerFactory.getLogger(LingoController.class);

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private WordRepository wordRepo;

	private final List<String> waitingList = new ArrayList<>();

	private final Map<String, Game> gameBySession = new HashMap<>();

	private final Map<String, Game> practiceBySession = new HashMap<>();

	private final Map<String, String> usernameBySession = new HashMap<>();

	@MessageMapping("/chat")
	public ChatMessage chat(String message, @Header(SESSION_ID_HEADER) String sessionId) {
		final String username = usernameBySession.get(sessionId);
		return new ChatMessage(username, message);
	}

	@MessageMapping("/guess")
	public void guess(String guess, @Header(SESSION_ID_HEADER) String sessionId) {
		guess = guess.toUpperCase();
		log.info("Player {} guessed: {}", sessionId, guess);
		final Game game = gameBySession.get(sessionId);
		final int[] result = game.evaluate(guess);

		// Generate reports
		final Report playerReport = new Report();
		final Report opponentReport = new Report();
		playerReport.setGuess(guess);
		if (Game.isCorrect(result)) {
			final String newWord = game.newWord();
			final String firstLetter = String.valueOf(newWord.charAt(0));
			log.info("New word: {}", newWord);
			playerReport.setCorrect(true);
			playerReport.setFirstLetter(firstLetter);
			opponentReport.setCorrect(true);
			opponentReport.setFirstLetter(firstLetter);
			opponentReport.setGuess(guess);
		} else {
			playerReport.setResult(result);
			opponentReport.setResult(result);
		}
		final String opponentId = sessionId.equals(game.playerOne) ? game.playerTwo : game.playerOne;
		sendToUser(sessionId, StompTopics.PLAYER_REPORTS, playerReport);
		sendToUser(opponentId, StompTopics.OPPONENT_REPORTS, opponentReport);
	}

	@MessageMapping("/join")
	public void join(String username, @Header(SESSION_ID_HEADER) String sessionId) {
		log.info("Player joined: {}, {}", sessionId, username);
		usernameBySession.put(sessionId, username);
		sendAnnouncement(username + " joined");
		joinWaitingList(sessionId);
	}

	private void joinWaitingList(String sessionId) {
		synchronized (waitingList) {
			if (!waitingList.contains(sessionId)) {
				waitingList.add(sessionId);
				waitingList.notify();
			}
		}
	}

	private void leave(String sessionId) {
		final String username = usernameBySession.remove(sessionId);
		if (username != null) {
			sendAnnouncement(username + " left");
		}
		final Game game = gameBySession.remove(sessionId);
		if (game == null) {
			leaveWaitingList(sessionId);
		} else {
			log.info("Player {} left their game!", sessionId);
			final String opponentId = sessionId.equals(game.playerOne) ? game.playerTwo : game.playerOne;
			gameBySession.remove(opponentId);
			sendToUser(opponentId, StompTopics.OPPONENT_LEFT, "You win!");
			joinWaitingList(opponentId);
		}
	}

	private void leaveWaitingList(String sessionId) {
		synchronized (waitingList) {
			waitingList.remove(sessionId);
			waitingList.notify();
		}
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
		log.info("Session connected: {}", sessionId);
	}

	private void onSessionDisconnect(SessionDisconnectEvent event) {
		final String sessionId = event.getSessionId();
		log.info("Session disconnected: {}", sessionId);
		leave(sessionId);
	}

	@PostConstruct
	private void postConstruct() {
		new Thread(new WaitingListListener()).start();
	}

	@MessageMapping("/practiceGame")
	public void practiceGame(@Header(SESSION_ID_HEADER) String sessionId) {
		log.info("Player wants a practice session: {}", sessionId);
		final Game game = new Game(sessionId, null, wordRepo.getWords(), wordRepo.getGuesses());
		practiceBySession.put(sessionId, game);
		final String firstWord = game.newGame();
		final String firstLetter = String.valueOf(firstWord.charAt(0));
		log.info("First word: {}", firstWord);
		sendToUser(sessionId, StompTopics.PRACTICE_GAME, firstLetter);
	}

	@MessageMapping("/practiceGuess")
	public void practiceGuess(String guess, @Header(SESSION_ID_HEADER) String sessionId) {
		guess = guess.toUpperCase();
		log.info("Player {} guessed: {}", sessionId, guess);
		final Game game = practiceBySession.get(sessionId);
		final int[] result = game.evaluate(guess);

		// Generate report
		final Report report = new Report();
		report.setGuess(guess);
		if (Game.isCorrect(result)) {
			final String newWord = game.newWord();
			final String firstLetter = String.valueOf(newWord.charAt(0));
			log.info("New word: {}", newWord);
			report.setCorrect(true);
			report.setFirstLetter(firstLetter);
		} else {
			report.setResult(result);
		}
		sendToUser(sessionId, StompTopics.PRACTICE_REPORTS, report);
	}

	private void sendAnnouncement(String message) {
		final ChatMessage payload = new ChatMessage(null, message);
		messagingTemplate.convertAndSend(StompTopics.CHAT, payload);
	}

	private void sendToUser(String user, String destination, Object payload) {
		// TODO: cache the headers?
		final SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(user);
		headerAccessor.setLeaveMutable(true);
		final MessageHeaders headers = headerAccessor.getMessageHeaders();
		messagingTemplate.convertAndSendToUser(user, destination, payload, headers);
	}

	/**
	 * Task that spawns a game whenever two players are waiting.
	 */
	private class WaitingListListener implements Runnable {

		@Override
		public void run() {
			while (true) {
				final String playerOne;
				final String playerTwo;
				synchronized (waitingList) {
					while (waitingList.size() < 2) {
						try {
							waitingList.wait();
						} catch (InterruptedException ok) {
							ok.printStackTrace();
						}
					}
					playerOne = waitingList.remove(0);
					playerTwo = waitingList.remove(0);
				}
				final Game game = new Game(playerOne, playerTwo, wordRepo.getWords(), wordRepo.getGuesses());
				gameBySession.put(playerOne, game);
				gameBySession.put(playerTwo, game);
				final String firstWord = game.newGame();
				final String firstLetter = String.valueOf(firstWord.charAt(0));
				log.info("First word: {}", firstWord);
				final String playerOneUsername = usernameBySession.get(playerOne);
				final String playerTwoUsername = usernameBySession.get(playerTwo);
				final String[] playerOneMessage = new String[] { firstLetter, playerTwoUsername };
				final String[] playerTwoMessage = new String[] { firstLetter, playerOneUsername };
				sendToUser(playerOne, StompTopics.OPPONENT_JOINED, playerOneMessage);
				sendToUser(playerTwo, StompTopics.OPPONENT_JOINED, playerTwoMessage);
				sendAnnouncement(playerOneUsername + " is playing with " + playerTwoUsername);
			}
		}
	}

}
