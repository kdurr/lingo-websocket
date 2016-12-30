package lingo.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;

public class PlayerBoard extends GameBoard {

	/** Tracks the player's current guess */
	private final StringBuilder guess = new StringBuilder();

	/** Tracks the player's previous guesses */
	private final List<String> guesses = new ArrayList<>();

	/** Tracks the player's progress toward the current word */
	private final Map<Integer, String> progress = new HashMap<>();

	public PlayerBoard(Canvas canvas, double xInit, double yInit) {
		super(canvas, xInit, yInit);
	}

	@Override
	public void clearBoard() {
		super.clearBoard();
		guess.setLength(0);
		guesses.clear();
		progress.clear();
		results.clear();
	}

	@Override
	public void drawBoard() {
		drawScore();
		drawInput();
		drawResults();
		double yStart = drawGuesses();
		drawHint(yStart);
		drawGrid();
	}

	private void drawInput() {
		gc.setFill(Color.GREEN);
		double x = xInit + SIDE * 0.5;
		double y = yInit + SIDE * 0.5;
		for (int i = 0; i < guess.length(); i++) {
			String character = String.valueOf(guess.charAt(i));
			gc.fillText(character, x, y);
			x += SIDE;
		}
	}

	private double drawGuesses() {
		double y = yInit + SIDE * 1.5;
		double numGuesses = Math.min(4, guesses.size());
		for (int i = 0; i < numGuesses; i++) {
			double x = xInit + SIDE * 0.5;
			String guess = guesses.get((int) (guesses.size() - numGuesses + i));
			for (int j = 0; j < 5; j++) {
				String character = String.valueOf(guess.charAt(j));
				gc.setFill(Color.GREEN);
				gc.fillText(character, x, y);
				x += SIDE;
			}
			y += SIDE;
		}
		return y;
	}

	private void drawHint(double yStart) {
		double x = xInit + SIDE * 0.5;
		for (int i = 0; i < 5; i++) {
			if (progress.containsKey(i)) {
				gc.fillText(progress.get(i), x, yStart);
			}
			x += SIDE;
		}
	}

	public void addGuess(String value) {
		guesses.add(value);
	}

	public String getGuess() {
		return guess.toString();
	}

	public boolean handleBackspace() {
		if (guess.length() > 0) {
			guess.setLength(guess.length() - 1);
			return true;
		}
		return false;
	}

	public String handleEnter() {
		if (guess.length() == 5) {
			final String value = guess.toString();
			guess.setLength(0);
			return value;
		}
		return null;
	}

	public boolean handleLetter(String letter) {
		if (guess.length() < 5) {
			guess.append(letter);
			return true;
		}
		return false;
	}

	public void setProgress(int i, char letter) {
		progress.put(i, String.valueOf(letter));
	}

}
