package lingo.client.multiplayer;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebView;
import lingo.client.api.Destinations;
import lingo.client.util.FxmlController;
import lingo.client.view.Board;
import lingo.client.view.OpponentBoard;
import lingo.client.view.PlayerBoard;
import lingo.common.Game;
import lingo.common.Report;

@Component
public class MultiplayerPresenter implements FxmlController {

	private static final Logger log = LoggerFactory.getLogger(MultiplayerPresenter.class);

	private static final double MARGIN_BOTTOM = 75;

	@FXML
	private Button backButton;

	@FXML
	private Canvas canvas;

	@FXML
	private StackPane contentPane;

	@FXML
	private WebView webView;

	@Autowired
	private ExecutorService executorService;

	@Autowired
	private StompTemplate stompTemplate;

	private EventHandler<ActionEvent> backButtonHandler;

	private GraphicsContext gc;

	private String lastWord;

	private PlayerBoard playerBoard;

	private OpponentBoard opponentBoard;

	private final CountDownLatch subscriptionsLatch = new CountDownLatch(4);

	private void clearBoards(boolean clearScore) {
		playerBoard.clearBoard();
		opponentBoard.clearBoard();
		if (clearScore) {
			playerBoard.clearScore();
			opponentBoard.clearScore();
		}
	}

	private void drawLastWord() {
		if (lastWord != null) {
			double x = canvas.getWidth() / 2;
			double y = canvas.getHeight() - MARGIN_BOTTOM / 2;
			gc.setFill(Color.BLACK);
			gc.fillText("Previous word: " + lastWord.toUpperCase(), x, y);
		}
	}

	@Override
	public void initialize() {
		// Needed for key event handling
		canvas.setFocusTraversable(true);

		gc = canvas.getGraphicsContext2D();
		gc.setFont(Font.font(24));
		gc.setTextAlign(TextAlignment.CENTER);
		gc.setTextBaseline(VPos.CENTER);
		playerBoard = new PlayerBoard(canvas, 50, 50);
		opponentBoard = new OpponentBoard(canvas, 50 + Board.WIDTH + 50, 50);

		Platform.runLater(() -> {
			String html = getClass().getResource("/cube-grid.html").toExternalForm();
			String css = getClass().getResource("/cube-grid.css").toExternalForm();
			webView.getEngine().load(html);
			webView.getEngine().setUserStyleSheetLocation(css);
			webView.setContextMenuEnabled(false);
			repaint();
		});

		backButton.setOnAction(backButtonHandler);

		executorService.execute(() -> {
			while (subscriptionsLatch.getCount() != 0) {
				try {
					subscriptionsLatch.await();
				} catch (InterruptedException ok) {
					ok.printStackTrace();
				}
			}
			stompTemplate.getSession().send("/app/lingo/join", null);
		});
	}

	@FXML
	private void keyPressed(KeyEvent e) {
		final KeyCode keyCode = e.getCode();
		if (keyCode == KeyCode.BACK_SPACE) {
			if (playerBoard.handleBackspace()) {
				repaint();
			}
		} else if (keyCode == KeyCode.ENTER) {
			final String guess = playerBoard.handleEnter();
			if (guess != null) {
				executorService.execute(() -> stompTemplate.getSession().send("/app/lingo/guess", guess));
				repaint();
			}
		} else if (keyCode.isLetterKey()) {
			if (playerBoard.handleLetter(keyCode.getName())) {
				repaint();
			}
		}
	}

	private void newWord(String firstLetter) {
		playerBoard.setProgress(0, firstLetter.charAt(0));
	}

	@PostConstruct
	private void postConstruct() {
		executorService.execute(() -> {
			stompTemplate.subscribe("/user" + Destinations.OPPONENT_JOINED, new OpponentJoinedHandler(),
					subscription -> subscriptionsLatch.countDown());
			stompTemplate.subscribe("/user" + Destinations.OPPONENT_LEFT, new OpponentLeftHandler(),
					subscription -> subscriptionsLatch.countDown());
			stompTemplate.subscribe("/user" + Destinations.OPPONENT_REPORTS, new OpponentReportHandler(),
					subscription -> subscriptionsLatch.countDown());
			stompTemplate.subscribe("/user" + Destinations.PLAYER_REPORTS, new PlayerReportHandler(),
					subscription -> subscriptionsLatch.countDown());
		});
	}

	private void repaint() {
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		playerBoard.drawBoard();
		opponentBoard.drawBoard();
		drawLastWord();
	}

	public void setOnBackButtonPressed(EventHandler<ActionEvent> handler) {
		backButtonHandler = handler;
	}

	private void showWaitingAnimation(boolean show) {
		if (show) {
			contentPane.getChildren().add(webView);
			backButton.toFront();
		} else {
			contentPane.getChildren().remove(webView);
		}
	}

	private class OpponentJoinedHandler implements StompFrameHandler {

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return String.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			final String firstLetter = payload.toString();
			Platform.runLater(() -> {
				clearBoards(true);
				newWord(firstLetter);
				showWaitingAnimation(false);
				repaint();
			});
		}
	}

	private class OpponentLeftHandler implements StompFrameHandler {

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return String.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			Platform.runLater(() -> {
				clearBoards(true);
				showWaitingAnimation(true);
				lastWord = null;
				repaint();
			});
		}
	}

	private class OpponentReportHandler implements StompFrameHandler {

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return Report.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			handleMessage((Report) payload);
		}

		private void handleMessage(Report report) {
			if (report.isCorrect()) {
				onCorrectGuess(report);
			} else {
				onIncorrectGuess(report);
			}
		}

		private void onCorrectGuess(Report report) {
			final String guess = report.getGuess();
			final String firstLetter = report.getFirstLetter();
			log.info("Opponent guessed correctly: " + guess);
			Platform.runLater(() -> {
				opponentBoard.addToScore(100);
				lastWord = guess;
				clearBoards(false);
				newWord(firstLetter);
				repaint();
			});
		}

		private void onIncorrectGuess(Report report) {
			final int[] result = report.getResult();
			log.info("Opponent result: " + Arrays.toString(result));
			Platform.runLater(() -> {
				opponentBoard.addResult(result);
				repaint();
			});
		}
	}

	private class PlayerReportHandler implements StompFrameHandler {

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return Report.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			handleMessage((Report) payload);
		}

		private void handleMessage(Report report) {
			if (report.isCorrect()) {
				onCorrectGuess(report);
			} else {
				onIncorrectGuess(report);
			}
		}

		private void onCorrectGuess(Report report) {
			final String guess = report.getGuess();
			final String firstLetter = report.getFirstLetter();
			log.info("I guessed correctly!");
			Platform.runLater(() -> {
				playerBoard.addToScore(100);
				lastWord = guess;
				clearBoards(false);
				newWord(firstLetter);
				repaint();
			});
		}

		private void onIncorrectGuess(Report report) {
			final String guess = report.getGuess();
			final int[] result = report.getResult();
			log.info("My result: " + Arrays.toString(result));
			Platform.runLater(() -> {
				if (Arrays.equals(result, Game.INVALID_GUESS)) {
					playerBoard.addGuess("-----");
				} else {
					for (int i = 0; i < Game.WORD_LENGTH; i++) {
						if (result[i] == Game.CORRECT_CHARACTER) {
							playerBoard.setProgress(i, guess.charAt(i));
						}
					}
					playerBoard.addGuess(guess);
				}
				playerBoard.addResult(result);
				repaint();
			});
		}
	}

}
