package lingo.client.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;

public abstract class GameBoard extends Board {

	/** Tracks the player's previous guess evaluations */
	protected final List<int[]> results = new ArrayList<>();

	/** Tracks the player's score */
	protected int score;

	public GameBoard(Canvas canvas, double xInit, double yInit) {
		super(canvas, xInit, yInit);
	}

	@Override
	public void clearBoard() {
		super.clearBoard();
		results.clear();
	}

	protected void drawScore() {
		double scoreX = xInit + WIDTH / 2;
		double scoreY = yInit / 2;
		gc.setFill(Color.BLACK);
		gc.fillText(String.valueOf(score), scoreX, scoreY);
	}

	protected void drawGrid() {
		gc.beginPath();
		for (int x = 0; x <= WIDTH; x += SIDE) {
			gc.moveTo(xInit + x, yInit);
			gc.lineTo(xInit + x, yInit + HEIGHT);
		}
		for (int y = 0; y <= HEIGHT; y += SIDE) {
			gc.moveTo(xInit, yInit + y);
			gc.lineTo(xInit + WIDTH, yInit + y);
		}
		gc.setFill(Color.BLACK);
		gc.stroke();
	}

	protected void drawResults() {
		double y = yInit + SIDE * 1.5;
		int numResults = Math.min(4, results.size());
		for (int i = 0; i < numResults; i++) {
			double x = xInit + SIDE * 0.5;
			int[] result = results.get(results.size() - numResults + i);
			for (int j = 0; j < 5; j++) {
				if (result[j] == 1) {
					gc.setFill(Color.YELLOW);
					gc.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
				} else if (result[j] == 2) {
					gc.setFill(Color.ORANGE);
					gc.fillRect(x - SIDE * 0.5, y - SIDE * 0.5, SIDE, SIDE);
				}
				x += SIDE;
			}
			y += SIDE;
		}
	}

	public void addResult(int[] value) {
		results.add(value);
	}

	public void addToScore(int value) {
		score += value;
	}

	public void clearScore() {
		score = 0;
	}

}
