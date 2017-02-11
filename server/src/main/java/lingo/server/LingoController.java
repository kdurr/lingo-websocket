package lingo.server;

import static org.springframework.messaging.simp.SimpMessageHeaderAccessor.SESSION_ID_HEADER;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lingo.client.api.Destinations;
import lingo.common.ChatMessage;
import lingo.common.Game;
import lingo.common.GameLeftMessage;
import lingo.common.Player;
import lingo.common.Report;
import lingo.common.SetUsernameMessage;

@RestController
public class LingoController {

	private static final Logger log = LoggerFactory.getLogger(LingoController.class);

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private SessionManager sessionManager;

	@Autowired
	private WordRepository wordRepo;

	private final Map<Integer, Game> gameById = new TreeMap<>();

	private final Map<Player, Game> gameByPlayer = new HashMap<>();

	private final Set<String> usernames = new HashSet<>();

	@MessageMapping("/chat")
	public ChatMessage chat(String message, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = sessionManager.getPlayer(sessionId);
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
		final Player player = sessionManager.getPlayer(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		guess = guess.toUpperCase();
		log.info("{} guessed {}", player, guess);
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
		if (sessionId.equals(game.getPlayerOne().getSessionId())) {
			opponent = game.getPlayerTwo();
		} else {
			opponent = game.getPlayerOne();
		}
		sendToPlayer(player, Destinations.PLAYER_REPORTS, playerReport);
		sendToPlayer(opponent, Destinations.OPPONENT_REPORTS, opponentReport);
	}

	@MessageMapping("/hostGame")
	public synchronized void hostGame(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = sessionManager.getPlayer(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		if (gameByPlayer.containsKey(player)) {
			log.warn("{} is in a game already", player);
			return;
		}
		final Game game = new Game(player);
		gameById.put(game.id, game);
		gameByPlayer.put(player, game);
		log.info("{} hosted Game {}", player, game.id);
		send(Destinations.GAME_HOSTED, game);
	}

	@MessageMapping("/joinGame")
	public synchronized void joinGame(Integer gameId, @Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = sessionManager.getPlayer(sessionId);
		final String username = player.getUsername();
		if (username == null) {
			log.warn("No username for session {}", sessionId);
			return;
		}
		if (gameByPlayer.containsKey(player)) {
			log.warn("{} is in a game already", player);
			return;
		}
		final Game game = gameById.get(gameId);
		if (game == null) {
			log.warn("No game with id {}", gameId);
			return;
		}
		if (game.getPlayerTwo() == null) {
			game.setPlayerTwo(player);
			gameByPlayer.put(player, game);
			log.info("{} joined {}'s game", player, game.getPlayerOne());
			send(Destinations.GAME_JOINED, game);

			// Start the game immediately
			// TODO: require the players to "ready up"
			game.setAcceptableGuesses(wordRepo.getGuesses());
			game.setPossibleWords(wordRepo.getWords());

			final String firstWord = game.newGame();
			final String firstLetter = String.valueOf(firstWord.charAt(0));
			log.info("First word: {}", firstWord);
			final Player playerOne = game.getPlayerOne();
			final Player playerTwo = game.getPlayerTwo();
			final String[] playerOneMessage = new String[] { firstLetter, playerTwo.getUsername() };
			final String[] playerTwoMessage = new String[] { firstLetter, playerOne.getUsername() };
			sendToPlayer(playerOne, Destinations.OPPONENT_JOINED, playerOneMessage);
			sendToPlayer(playerTwo, Destinations.OPPONENT_JOINED, playerTwoMessage);
		}
	}

	private synchronized void leave(Player player) {
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
		final Player player = sessionManager.getPlayer(sessionId);
		final Game game = gameByPlayer.remove(player);
		if (game == null) {
			log.warn("{} is not in a game", player);
			return;
		}
		leaveGame(game, player);
	}

	private synchronized void leaveGame(Game game, Player player) {
		final Player playerOne = game.getPlayerOne();
		final Player playerTwo = game.getPlayerTwo();
		if (playerOne == player) {
			if (playerTwo == null) {
				// Close the game
				log.info("{} closed Game {}", player, game.id);
				gameById.remove(game.id);
				send(Destinations.GAME_CLOSED, game);
			} else {
				// Leave the game
				game.setPlayerOne(playerTwo);
				game.setPlayerTwo(null);
				send(Destinations.GAME_LEFT, new GameLeftMessage(game, player));
			}
		} else if (playerTwo == player) {
			// Leave the game
			game.setPlayerTwo(null);
			send(Destinations.GAME_LEFT, new GameLeftMessage(game, player));
		}
	}

	@SubscribeMapping("/topic/sessionId")
	public String onSessionId(@Header(SESSION_ID_HEADER) String sessionId) {
		return sessionId;
	}

	@PostConstruct
	public void postConstruct() {
		sessionManager.addListener(new PlayerLeftListener());
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
		final Player player = sessionManager.getPlayer(sessionId);
		if (usernames.add(username)) {
			player.setUsername(username);
			log.info("{} --> {}", sessionId, username);
			sendToPlayer(player, Destinations.SESSION_USERNAME, new SetUsernameMessage(true, username, null));
			send(Destinations.USER_JOINED, new Object[] { username, sessionManager.getPlayerCount() });
		} else {
			log.warn("{} -/> {} : Username taken", sessionId, username);
			final SetUsernameMessage response = new SetUsernameMessage(false, null, "Username taken");
			sendToPlayer(player, Destinations.SESSION_USERNAME, response);
		}
	}

	private class PlayerLeftListener implements SessionManager.Listener {

		@Override
		public void playerJoined(Player player) {
			// Ignore joining players for now
		}

		@Override
		public void playerLeft(Player player) {
			leave(player);
		}

	}

}
