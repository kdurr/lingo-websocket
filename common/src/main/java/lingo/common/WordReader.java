package lingo.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WordReader {

	private static void readFileToCollection(String filename, Collection<String> c) throws IOException {
		try (final InputStream stream = WordReader.class.getResourceAsStream(filename);
				final InputStreamReader streamReader = new InputStreamReader(stream);
				final BufferedReader bufferedReader = new BufferedReader(streamReader)) {
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				c.add(line);
			}
		}
	}

	public static List<String> readFileToList(String filename) throws IOException {
		final List<String> list = new ArrayList<>();
		readFileToCollection(filename, list);
		return list;
	}

	public static Set<String> readFileToSet(String filename) throws IOException {
		final Set<String> list = new HashSet<>();
		readFileToCollection(filename, list);
		return list;
	}

}
