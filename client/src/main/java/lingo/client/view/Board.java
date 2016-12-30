package lingo.client.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public abstract class Board {

	public static final double HEIGHT = 300;
	public static final double WIDTH = 250;
	public static final double SIDE = 50;

	protected final Canvas canvas;

	protected final GraphicsContext gc;

	/** The leftmost x-coordinate */
	protected final double xInit;

	/** The topmost y-coordinate */
	protected final double yInit;

	public Board(Canvas canvas, double xInit, double yInit) {
		this.canvas = canvas;
		this.gc = canvas.getGraphicsContext2D();
		this.xInit = xInit;
		this.yInit = yInit;
	}

	public void clearBoard() {
		gc.clearRect(xInit, yInit, WIDTH, HEIGHT);
	}

	public abstract void drawBoard();

}
