package lingo.client.singleplayer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import lingo.client.view.PlayerBoard;
import lingo.common.Game;
import lingo.common.Report;

public class SinglePlayerPresenter {

	private static final Logger log = LoggerFactory.getLogger(SinglePlayerPresenter.class);

	private final Button backButton;

	private final Canvas canvas;

	private final StackPane contentPane;

	private final GraphicsContext gc;

	private final PlayerBoard gameBoard;

	private final Game game;

	public SinglePlayerPresenter(List<String> words, Set<String> guesses, EventHandler<ActionEvent> backButtonHandler) {
		backButton = new Button("Back");
		backButton.getStyleClass().add("game-nav");
		StackPane.setAlignment(backButton, Pos.BOTTOM_LEFT);
		StackPane.setMargin(backButton, new Insets(0, 0, 10, 10));
		backButton.setOnAction(backButtonHandler);
		backButton.setPrefWidth(50);

		canvas = new Canvas(650, 420);
		canvas.setFocusTraversable(true);
		canvas.setOnKeyPressed(e -> keyPressed(e));
		gc = canvas.getGraphicsContext2D();
		gc.setFont(Font.font(24));
		gc.setTextAlign(TextAlignment.CENTER);
		gc.setTextBaseline(VPos.CENTER);

		contentPane = new StackPane();
		contentPane.getChildren().add(canvas);
		contentPane.getChildren().add(backButton);

		gameBoard = new PlayerBoard(canvas, 200, 60);
		game = new Game(null, null, words, guesses);
	}

	private void clearBoards(boolean clearScore) {
		gameBoard.clearBoard();
		if (clearScore) {
			gameBoard.clearScore();
		}
	}

	public Node getNode() {
		return contentPane;
	}

	private void keyPressed(KeyEvent e) {
		final KeyCode keyCode = e.getCode();
		if (keyCode == KeyCode.BACK_SPACE) {
			if (gameBoard.handleBackspace()) {
				repaint();
			}
		} else if (keyCode == KeyCode.ENTER) {
			final String guess = gameBoard.handleEnter();
			if (guess != null) {
				final int[] result = game.evaluate(guess);
				Report report = new Report();
				report.setGuess(guess);
				report.setResult(result);
				if (Game.isCorrect(result)) {
					final String newWord = game.newWord();
					final String firstLetter = String.valueOf(newWord.charAt(0));
					report.setCorrect(true);
					report.setFirstLetter(firstLetter);
					report.setResult(result);
					onCorrectGuess(report);
				} else {
					onIncorrectGuess(report);
				}
			}
		} else if (keyCode.isLetterKey()) {
			if (gameBoard.handleLetter(keyCode.getName())) {
				repaint();
			}
		}
	}

	private void newWord(String firstLetter) {
		gameBoard.setProgress(0, firstLetter.charAt(0));
	}

	private void onCorrectGuess(Report report) {
		final String firstLetter = report.getFirstLetter();
		log.info("I guessed correctly!");
		Platform.runLater(() -> {
			gameBoard.addToScore(100);
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
				gameBoard.addGuess("-----");
			} else {
				for (int i = 0; i < Game.WORD_LENGTH; i++) {
					if (result[i] == Game.CORRECT_CHARACTER) {
						gameBoard.setProgress(i, guess.charAt(i));
					}
				}
				gameBoard.addGuess(guess);
			}
			gameBoard.addResult(result);
			repaint();
		});
	}

	private void repaint() {
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		gameBoard.drawBoard();
	}

	public void startGame() {
		final String firstWord = game.newGame();
		final String firstLetter = String.valueOf(firstWord.charAt(0));
		newWord(firstLetter);
		repaint();
	}

}
