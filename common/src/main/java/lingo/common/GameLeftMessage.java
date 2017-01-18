package lingo.common;

public class GameLeftMessage {

	private Game game;

	private Player gameLeaver;

	public GameLeftMessage() {}

	public GameLeftMessage(Game game, Player gameLeaver) {
		this.game = game;
		this.gameLeaver = gameLeaver;
	}

	public Game getGame() {
		return game;
	}

	public Player getGameLeaver() {
		return gameLeaver;
	}

	public void setGame(Game game) {
		this.game = game;
	}

	public void setGameLeaver(Player gameLeaver) {
		this.gameLeaver = gameLeaver;
	}

}
