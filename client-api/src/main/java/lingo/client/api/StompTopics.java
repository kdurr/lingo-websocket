package lingo.client.api;

public class StompTopics {

	public static final String CHAT = createTopicName("chat");

	public static final String GAME_STARTED = createTopicName("gameStarted");

	public static final String OPPONENT_JOINED = createTopicName("opponentJoined");

	public static final String OPPONENT_LEFT = createTopicName("opponentLeft");

	public static final String OPPONENT_REPORTS = createTopicName("opponentReports");

	public static final String PLAYER_REPORTS = createTopicName("playerReports");

	public static final String PRACTICE_GAME = createTopicName("practiceGame");

	public static final String PRACTICE_REPORTS = createTopicName("practiceReports");

	public static final String USER_JOINED = createTopicName("userJoined");

	private static String createTopicName(String suffix) {
		return "/topic/lingo/" + suffix;
	}

}
