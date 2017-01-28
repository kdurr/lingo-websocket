package lingo.server;

import static org.springframework.messaging.simp.SimpMessageHeaderAccessor.SESSION_ID_HEADER;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lingo.client.api.Destinations;
import lingo.common.ChatMessage;
import lingo.common.Game;
import lingo.common.GameLeftMessage;
import lingo.common.Player;
import lingo.common.Report;
import lingo.common.SetUsernameMessage;

@RestController
public class LingoController implements ApplicationListener<AbstractSubProtocolEvent> {

	private static final Logger log = LoggerFactory.getLogger(LingoController.class);

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private WordRepository wordRepo;

	private final Map<Integer, Game> gameById = new TreeMap<>();

	private final Map<Player, Game> gameByPlayer = new HashMap<>();

	private final Map<Player, Game> practiceByPlayer = new HashMap<>();

	private final Map<String, Player> playerBySession = new HashMap<>();

	private final Set<String> usernames = new HashSet<>();

	@MessageMapping("/chat")
	public ChatMessage chat(String message, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			throw new IllegalStateException("No username for session " + sessionId);
		}
		return new ChatMessage(player.getUsername(), message);
	}

	@RequestMapping("/games")
	public Collection<Game> getGames() {
		return gameById.values();
	}

	@MessageMapping("/guess")
	public void guess(String guess, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		guess = guess.toUpperCase();
		log.info("{} guessed {}", sessionId, guess);
		final Game game = gameByPlayer.get(player);
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
		final Player opponent;
		if (sessionId.equals(game.getHost().getSessionId())) {
			opponent = game.getChallenger();
		} else {
			opponent = game.getHost();
		}
		sendToPlayer(player, Destinations.PLAYER_REPORTS, playerReport);
		sendToPlayer(opponent, Destinations.OPPONENT_REPORTS, opponentReport);
	}

	@MessageMapping("/hostGame")
	public synchronized void hostGame(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		if (gameByPlayer.containsKey(player)) {
			log.warn("{} is in a game already", username);
			return;
		}
		final Game game = new Game(player);
		gameById.put(game.id, game);
		gameByPlayer.put(player, game);
		log.info("{} hosted a game", username);
		send(Destinations.GAME_HOSTED, game);
	}

	@MessageMapping("/joinGame")
	public synchronized void joinGame(Integer gameId, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		if (gameByPlayer.containsKey(player)) {
			log.warn("{} is in a game already", username);
			return;
		}
		final Game game = gameById.get(gameId);
		if (game == null) {
			log.warn("No game with id {}", gameId);
			return;
		}
		if (game.getChallenger() == null) {
			game.setChallenger(player);
			gameByPlayer.put(player, game);
			log.info("{} joined {}'s game", username, game.getHost());
			send(Destinations.GAME_JOINED, game);

			// Start the game immediately
			// TODO: require the players to "ready up"
			game.setAcceptableGuesses(wordRepo.getGuesses());
			game.setPossibleWords(wordRepo.getWords());
			final String firstWord = game.newGame();
			final String firstLetter = String.valueOf(firstWord.charAt(0));
			log.info("First word: {}", firstWord);
			final Player playerOne = game.getHost();
			final Player playerTwo = game.getChallenger();
			final String[] playerOneMessage = new String[] { firstLetter, playerTwo.getUsername() };
			final String[] playerTwoMessage = new String[] { firstLetter, playerOne.getUsername() };
			sendToPlayer(playerOne, Destinations.OPPONENT_JOINED, playerOneMessage);
			sendToPlayer(playerTwo, Destinations.OPPONENT_JOINED, playerTwoMessage);
			send(Destinations.GAME_STARTED, new String[] { playerOne.getUsername(), playerTwo.getUsername() });
		}
	}

	private synchronized void leave(String sessionId) {
		final Player player = playerBySession.remove(sessionId);
		final String username = player.getUsername();
		usernames.remove(username);
		final Game game = gameByPlayer.remove(player);
		if (game == null) {
			if (username != null) {
				log.info("{} left", username);
				send(Destinations.CHAT, new ChatMessage(null, username + " left"));
			}
		} else {
			leaveGame(game, player);
		}
	}

	@MessageMapping("/leaveGame")
	public synchronized void leaveGame(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		final Game game = gameByPlayer.remove(player);
		if (game == null) {
			log.warn("{} is not in a game", player.getUsername());
			return;
		}
		leaveGame(game, player);
	}

	private synchronized void leaveGame(Game game, Player player) {
		final Player gameHost = game.getHost();
		final Player gameChallenger = game.getChallenger();
		if (gameHost == player) {
			if (gameChallenger == null) {
				// Close the game
				gameById.remove(game.id);
				send(Destinations.GAME_CLOSED, game);
			} else {
				// Leave the game
				game.setHost(gameChallenger);
				game.setChallenger(null);
				send(Destinations.GAME_LEFT, new GameLeftMessage(game, player));
			}
		} else if (gameChallenger == player) {
			// Leave the game
			game.setChallenger(null);
			send(Destinations.GAME_LEFT, new GameLeftMessage(game, player));
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
		playerBySession.put(sessionId, new Player(sessionId));
	}

	private void onSessionDisconnect(SessionDisconnectEvent event) {
		final String sessionId = event.getSessionId();
		log.info("Session disconnected: {}", sessionId);
		leave(sessionId);
	}

	@SubscribeMapping("/topic/sessionId")
	public String onSessionId(@Header(SESSION_ID_HEADER) String sessionId) {
		return sessionId;
	}

	@MessageMapping("/practiceGame")
	public void practiceGame(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		log.info("{} wants a practice session", sessionId);
		final Game game = new Game(player);
		game.setAcceptableGuesses(wordRepo.getGuesses());
		game.setPossibleWords(wordRepo.getWords());
		practiceByPlayer.put(player, game);
		final String firstWord = game.newGame();
		final String firstLetter = String.valueOf(firstWord.charAt(0));
		log.info("First word: {}", firstWord);
		sendToPlayer(player, Destinations.PRACTICE_GAME, firstLetter);
	}

	@MessageMapping("/practiceGuess")
	public void practiceGuess(String guess, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		guess = guess.toUpperCase();
		log.info("{} guessed {}", sessionId, guess);
		final Game game = practiceByPlayer.get(player);
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
		sendToPlayer(player, Destinations.PRACTICE_REPORTS, report);
	}

	private void send(String destination, Object payload) {
		messagingTemplate.convertAndSend(destination, payload);
	}

	private void sendToPlayer(Player player, String destination, Object payload) {
		sendToSession(player.getSessionId(), destination, payload);
	}

	private void sendToSession(String sessionId, String destination, Object payload) {
		// TODO: cache the headers?
		final SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setLeaveMutable(true);
		final MessageHeaders headers = headerAccessor.getMessageHeaders();
		messagingTemplate.convertAndSendToUser(sessionId, destination, payload, headers);
	}

	@MessageMapping("/setUsername")
	public synchronized void setUsername(String username, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = playerBySession.get(sessionId);
		if (usernames.add(username)) {
			player.setUsername(username);
			log.info("{} --> {}", sessionId, username);
			sendToPlayer(player, Destinations.SESSION_USERNAME, new SetUsernameMessage(true, username, null));
			send(Destinations.USER_JOINED, new Object[] { username, playerBySession.size() });
		} else {
			log.warn("{} -/> {} : Username taken", sessionId, username);
			final SetUsernameMessage response = new SetUsernameMessage(false, null, "Username taken");
			sendToPlayer(player, Destinations.SESSION_USERNAME, response);
		}
	}

}
