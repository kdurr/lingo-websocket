package lingo.client.api;

public class StompTopics {

	public static final String OPPONENT_JOINED = createTopicName("opponentJoined");

	public static final String OPPONENT_LEFT = createTopicName("opponentLeft");

	public static final String OPPONENT_REPORTS = createTopicName("opponentReports");

	public static final String PLAYER_REPORTS = createTopicName("playerReports");

	private static String createTopicName(String suffix) {
		return "/topic/lingo/" + suffix;
	}

}
