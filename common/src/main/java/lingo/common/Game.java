package lingo.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Game {

	public static final int INCORRECT_CHARACTER = 0;
	public static final int INCORRECT_POSITION = 1;
	public static final int CORRECT_CHARACTER = 2;
	public static final int WORD_LENGTH = 5;

	/** Nein nein nein nein nein! */
	private static final int[] INVALID_GUESS = new int[] { 9, 9, 9, 9, 9 };

	private static final AtomicInteger idCounter = new AtomicInteger(0);

	public final int id;

	private Player playerOne;

	private Player playerTwo;

	private Set<String> acceptableGuesses;

	private List<String> possibleWords;

	private String word;

	private int wordIndex = 0;

	public Game(Player host) {
		this.id = idCounter.incrementAndGet();
		this.playerOne = host;
	}

	private static int indexOf(char[] array, char searchTerm) {
		for (int i = 0; i < WORD_LENGTH; i++) {
			if (array[i] == searchTerm) {
				return i;
			}
		}
		return -1;
	}

	public static boolean isCorrect(int[] result) {
		for (int i = 0; i < WORD_LENGTH; i++) {
			if (result[i] != CORRECT_CHARACTER) {
				return false;
			}
		}
		return true;
	}

	public static boolean isInvalid(int[] result) {
		return Arrays.equals(result, INVALID_GUESS);
	}

	public int[] evaluate(String guess) {
		if (!acceptableGuesses.contains(guess)) {
			return INVALID_GUESS;
		}

		// the guess is acceptable
		int[] result = new int[WORD_LENGTH];
		char[] remaining = new char[WORD_LENGTH];
		for (int i = 0; i < WORD_LENGTH; i++) {
			if (guess.charAt(i) == word.charAt(i)) {
				result[i] = CORRECT_CHARACTER;
			} else {
				result[i] = INCORRECT_CHARACTER;
				remaining[i] = word.charAt(i);
			}
		}
		for (int i = 0; i < WORD_LENGTH; i++) {
			if (result[i] == INCORRECT_CHARACTER) {
				int index = indexOf(remaining, guess.charAt(i));
				if (index != -1) {
					result[i] = INCORRECT_POSITION;
					remaining[index] = 0;
				}
			}
		}
		return result;
	}

	public Player getPlayerOne() {
		return playerOne;
	}

	public Player getPlayerTwo() {
		return playerTwo;
	}

	public String newGame() {
		Collections.shuffle(possibleWords);
		wordIndex = 0;
		return newWord();
	}

	public String newWord() {
		word = possibleWords.get(wordIndex++);
		return word;
	}

	public void setAcceptableGuesses(Set<String> value) {
		this.acceptableGuesses = value;
	}

	public void setPlayerOne(Player value) {
		this.playerOne = value;
	}

	public void setPlayerTwo(Player value) {
		this.playerTwo = value;
	}

	public void setPossibleWords(List<String> value) {
		this.possibleWords = value;
	}

}
