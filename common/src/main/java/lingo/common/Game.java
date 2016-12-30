package lingo.common;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Game {

	public static final int INCORRECT_CHARACTER = 0;
	public static final int INCORRECT_POSITION = 1;
	public static final int CORRECT_CHARACTER = 2;
	public static final int WORD_LENGTH = 5;
	public static final int[] INVALID_GUESS = new int[] { 9, 9, 9, 9, 9 };

	public final String playerOne;

	public final String playerTwo;

	private final Set<String> acceptableGuesses;

	private final List<String> possibleWords;

	private String word;

	private int wordIndex = 0;

	public Game(String playerOne, String playerTwo, List<String> possibleWords, Set<String> acceptableGuesses) {
		this.playerOne = playerOne;
		this.playerTwo = playerTwo;
		this.possibleWords = possibleWords;
		this.acceptableGuesses = acceptableGuesses;
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

	public String newGame() {
		Collections.shuffle(possibleWords);
		wordIndex = 0;
		return newWord();
	}

	public String newWord() {
		word = possibleWords.get(wordIndex++);
		return word;
	}

}
