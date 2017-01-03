package lingo.client.bootstrap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

@SpringBootApplication
public class LingoClient extends Application {

	private Parent root;

	public static void main(final String[] args) {
		Application.launch(args);
	}

	@Override
	public void init() throws Exception {
		ConfigurableApplicationContext context = startSpringApplication();
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Lingo.fxml"));
		loader.setControllerFactory(clazz -> context.getBean(clazz));
		root = loader.load();
	}

	@Override
	public void start(Stage stage) throws Exception {
		// Close the Spring context when the client is closed.
		stage.setOnCloseRequest(e -> {
			stage.close();
			System.exit(0);
		});
		Scene scene = new Scene(root);
		scene.getStylesheets().add("/style.css");
		stage.getIcons().add(new Image(getClass().getResourceAsStream("/lingo.png")));
		stage.setResizable(false);
		stage.setScene(scene);
		stage.setTitle("Lingo");
		stage.show();
	}

	private ConfigurableApplicationContext startSpringApplication() {
		SpringApplication application = new SpringApplication(LingoClient.class);
		String[] args = getParameters().getRaw().stream().toArray(String[]::new);
		application.setHeadless(false);
		application.setWebEnvironment(false);
		return application.run(args);
	}

	@Bean
	public ExecutorService executorService() {
		return Executors.newFixedThreadPool(5, new CustomizableThreadFactory("ClientThread-"));
	}

}
