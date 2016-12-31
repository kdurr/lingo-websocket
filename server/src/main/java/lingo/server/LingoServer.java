package lingo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LingoServer {

	public static void main(String[] args) {

		// Heroku dynamically assigns a port
		final String webPort = System.getenv("PORT");
		if (webPort != null && !webPort.isEmpty()) {
			System.setProperty("server.port", webPort);
		}

		SpringApplication.run(LingoServer.class, args);
	}

}
