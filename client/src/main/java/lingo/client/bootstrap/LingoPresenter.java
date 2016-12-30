package lingo.client.bootstrap;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lingo.client.multiplayer.MultiplayerConfig;
import lingo.client.multiplayer.MultiplayerPresenter;
import lingo.client.singleplayer.SinglePlayerPresenter;
import lingo.client.util.FxmlController;
import lingo.common.WordReader;

@Component
public class LingoPresenter implements FxmlController {

	private static final Logger log = LoggerFactory.getLogger(LingoPresenter.class);

	@Autowired
	private ApplicationContext bootstrapContext;

	@Autowired
	private ExecutorService executorService;

	@FXML
	private BorderPane content;

	@FXML
	private BorderPane gameModeChooser;

	@FXML
	private void exit(ActionEvent event) {
		Stage stage = (Stage) content.getScene().getWindow();
		stage.close();
		System.exit(0);
	}

	@Override
	public void initialize() {
		// No initialization needed
	}

	@FXML
	private void showMultiplayer(ActionEvent event) {
		log.info("Launching multiplayer...");

		executorService.execute(() -> {
			AnnotationConfigApplicationContext multiplayerContext = new AnnotationConfigApplicationContext();
			multiplayerContext.setParent(bootstrapContext);
			multiplayerContext.scan(MultiplayerConfig.class.getPackage().getName());
			multiplayerContext.refresh();

			MultiplayerPresenter presenter = multiplayerContext.getBean(MultiplayerPresenter.class);
			presenter.setOnBackButtonPressed(e -> {
				multiplayerContext.close();
				content.setCenter(gameModeChooser);
			});

			Platform.runLater(() -> {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LingoMultiplayer.fxml"));
				loader.setControllerFactory(clazz -> multiplayerContext.getBean(clazz));
				try {
					content.setCenter(loader.load());
				} catch (IOException e) {
					log.error("Failed to load multiplayer", e);
				}
			});
		});
	}

	@FXML
	private void showSinglePlayer(ActionEvent event) {
		log.info("Launching single player...");

		// TODO: Is there a memory leak here?
		try {
			Set<String> guesses = WordReader.readFileToSet("/guesses.txt");
			List<String> words = WordReader.readFileToList("/words.txt");
			SinglePlayerPresenter presenter = new SinglePlayerPresenter(words, guesses, e -> {
				content.setCenter(gameModeChooser);
			});
			content.setCenter(presenter.getNode());
			presenter.startGame();
		} catch (IOException e) {
			log.error("Failed to load single player", e);
		}
	}

}
