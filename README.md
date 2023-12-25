# Manual
To build the project use ```gradlew shadowJar``` command.

To run the project use command 

`java --module-path path/to/javaFX/lib --add-modules javafx.controls,javafx.fxml --add-exports javafx.graphics/com.sun.javafx.text=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.scene.text=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.text=ALL-UNNAMED --add-opens javafx.graphics/com.sun.javafx.scene.text=ALL-UNNAMED --add-opens javafx.graphics/javafx.scene.text=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED -jar app.jar`

change `path/to/javaFX/lib` to real path

# Or use compiled version
## Go to branch `release`
