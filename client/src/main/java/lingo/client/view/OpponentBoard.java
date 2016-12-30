package lingo.client.view;

import javafx.scene.canvas.Canvas;

public class OpponentBoard extends GameBoard {

	public OpponentBoard(Canvas canvas, double xInit, double yInit) {
		super(canvas, xInit, yInit);
	}

	@Override
	public void drawBoard() {
		drawScore();
		drawResults();
		drawGrid();
	}

}
