package lingo.common;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Player {

	@JsonIgnore
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

	public void setUsername(String value) {
		this.username = value;
	}

	@Override
	public String toString() {
		if (username != null) {
			return username;
		}
		return sessionId;
	}

}
