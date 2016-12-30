package lingo.common;

public class Report {

	private boolean correct;

	private String firstLetter;

	private String guess;

	private int[] result;

	public Report() {}

	public String getFirstLetter() {
		return firstLetter;
	}

	public void setFirstLetter(String value) {
		this.firstLetter = value;
	}

	public String getGuess() {
		return guess;
	}

	public void setGuess(String value) {
		this.guess = value;
	}

	public int[] getResult() {
		return result;
	}

	public void setResult(int[] value) {
		this.result = value;
	}

	public boolean isCorrect() {
		return correct;
	}

	public void setCorrect(boolean value) {
		this.correct = value;
	}

}
