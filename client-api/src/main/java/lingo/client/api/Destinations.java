package lingo.client.api;

public class Destinations {

	public static final String CHAT = topicDestination("chat");

	public static final String GAME_CLOSED = topicDestination("gameClosed");

	public static final String GAME_HOSTED = topicDestination("gameHosted");

	public static final String GAME_JOINED = topicDestination("gameJoined");

	public static final String GAME_LEFT = topicDestination("gameLeft");

	public static final String GAME_STARTED = topicDestination("gameStarted");

	public static final String OPPONENT_JOINED = topicDestination("opponentJoined");

	public static final String OPPONENT_REPORTS = topicDestination("opponentReports");

	public static final String PLAYER_REPORTS = topicDestination("playerReports");

	public static final String PRACTICE_GAME = topicDestination("practiceGame");

	public static final String PRACTICE_REPORTS = topicDestination("practiceReports");

	public static final String PRACTICE_WORD_SKIPPED = topicDestination("practiceWordSkipped");

	public static final String SESSION_USERNAME = topicDestination("sessionUsername");

	public static final String USER_JOINED = topicDestination("userJoined");

	private static String topicDestination(String suffix) {
		return "/topic/" + suffix;
	}

}
