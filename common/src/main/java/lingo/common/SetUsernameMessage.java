package lingo.common;

public class SetUsernameMessage {

	private String errorMessage;

	private boolean success;

	private String username;

	public SetUsernameMessage() {}

	public SetUsernameMessage(boolean success, String username, String errorMessage) {
		this.errorMessage = errorMessage;
		this.success = success;
		this.username = username;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getUsername() {
		return username;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public void setUsername(String username) {
		this.username = username;
	}

}
