package lingo.common;

public class Player {

	private final String sessionId;

	private String username;

	public Player(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public String toString() {
		if (username != null) {
			return username;
		}
		return sessionId;
	}

}
