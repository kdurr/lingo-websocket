package lingo.server;

import static org.springframework.messaging.simp.SimpMessageHeaderAccessor.SESSION_ID_HEADER;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import lingo.client.api.Destinations;
import lingo.common.Game;
import lingo.common.Player;
import lingo.common.Report;

@RestController
public class PracticeController {

	private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private SessionManager sessionManager;

	@Autowired
	private WordRepository wordRepo;

	private final Map<Player, Game> practiceByPlayer = new HashMap<>();

	@MessageMapping("/practiceGame")
	public void practiceGame(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = sessionManager.getPlayer(sessionId);
		log.info("{} is practicing", sessionId);
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
		final Player player = sessionManager.getPlayer(sessionId);
		guess = guess.toUpperCase();
		log.info("{} guessed {}", player, guess);
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

	@MessageMapping("/practiceSkip")
	public void practiceSkip(@Header(SESSION_ID_HEADER) String sessionId) {
		final Player player = sessionManager.getPlayer(sessionId);
		final Game game = practiceByPlayer.get(player);
		final String newWord = game.newWord();
		final String firstLetter = String.valueOf(newWord.charAt(0));
		log.info("New word: {}", newWord);
		sendToPlayer(player, Destinations.PRACTICE_WORD_SKIPPED, firstLetter);
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

}
