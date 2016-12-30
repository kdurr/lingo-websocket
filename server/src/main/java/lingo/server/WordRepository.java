package lingo.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lingo.common.WordReader;

@Component
public class WordRepository {

	private final Set<String> guesses;

	private final List<String> words;

	public WordRepository() throws IOException {
		guesses = WordReader.readFileToSet("/guesses.txt");
		words = WordReader.readFileToList("/words.txt");
	}

	/**
	 * Returns the set of acceptable guesses (unmodifiable).
	 */
	public Set<String> getGuesses() {
		return Collections.unmodifiableSet(guesses);
	}

	/**
	 * Returns a copy of the list of potential answers (OK to shuffle it).
	 */
	public List<String> getWords() {
		return new ArrayList<>(words);
	}

}
