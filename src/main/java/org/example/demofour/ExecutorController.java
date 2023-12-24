package org.example.demofour;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import org.example.demofour.executor.MyExecutor;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutorController implements Initializable {
    private final MyExecutor executor = new MyExecutor();
    public ListView<Label> output;
    public ProgressIndicator progressIndicator;
    @FXML
    private StyleClassedTextArea input;
    @FXML
    private AnchorPane placeForCodeArea;
    private CodeArea codeArea;
    private String sourceCode;
    @FXML
    private Button runButton;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeCodeArea();
        initializeInputField();
        initializeRunButton();
        initializeOutputField();
    }

    private void initializeOutputField() {
        output.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                Label selectedLabel = output.getSelectionModel().getSelectedItem();
                if (selectedLabel != null) {
                    final Clipboard clipboard = Clipboard.getSystemClipboard();
                    final ClipboardContent content = new ClipboardContent();
                    content.putString(selectedLabel.getText());
                    clipboard.setContent(content);
                }
            }
        });
    }

    private void initializeRunButton() {
        runButton.setOnAction(event -> run());
    }

    private void initializeInputField() {
        input.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String userInput = input.getText();
                for (String str : userInput.split("\\R")) {
                    executor.sendInputToProcess(str);
                }
                input.clear();
                event.consume();
            }
        });
    }

    private void initializeCodeArea() {
        codeArea = new CodeArea();
        IntFunction<Node> numberFactory = LineNumberFactory.get(codeArea);
        codeArea.setParagraphGraphicFactory(numberFactory);

        AnchorPane.setTopAnchor(codeArea, 0d);
        AnchorPane.setBottomAnchor(codeArea, 0d);
        AnchorPane.setLeftAnchor(codeArea, 0d);
        AnchorPane.setRightAnchor(codeArea, 0d);

        placeForCodeArea.getChildren().add(codeArea);

        codeArea.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        codeArea.richChanges()
                .filter(ch -> !ch.getInserted().equals(ch.getRemoved())) // ignore style changes
                .subscribe(change -> {
                    codeArea.setStyleSpans(0, SyntaxHighlight.computeHighlighting(codeArea.getText()));
                });
        for (int i = 0; i < 50; i++) {
            codeArea.appendText("\n");
        }
    }

    private void run() {
        sourceCode = codeArea.getText();
        generateKotlin(sourceCode);

        changeIndicatorState(true);
        runButton.setVisible(false);
        executor.execute(
                this::addLineToConsole,
                this::onProgramStart,
                this::onProgramEnd);
    }

    private void addLineToConsole(String lines) {
        for (String line : lines.split("\n")) {
            Label textField = new Label(line);
            Pattern errorPattern = Pattern.compile(".*\\.kts:(\\d+):\\d+: .*");
            Matcher errorMatcher = errorPattern.matcher(line);
            Pattern exceptionPattern = Pattern.compile(".*\\.kts:(\\d+).*");
            Matcher exceptionMatcher = exceptionPattern.matcher(line);
            Platform.runLater(() -> {
                textField.setOnMouseClicked((event) -> errorLabelOnClick(line, errorPattern, exceptionPattern));
                output.getItems().add(textField);
            });

            if (errorMatcher.find()) {
                textField.setStyle("-fx-text-fill: red;");
            }

            if (exceptionMatcher.find()) {
                textField.setStyle("-fx-text-fill: red;");
            }
        }
    }

    private void errorLabelOnClick(String line, Pattern errorPattern, Pattern exceptionPattern) {
        Matcher errorMatcher = errorPattern.matcher(line);
        if (errorMatcher.find()) {
            int lineNumber = Integer.parseInt(errorMatcher.group(1));
            moveToErrorLine(lineNumber);
        }

        Matcher exceptionMatcher = exceptionPattern.matcher(line);
        if (exceptionMatcher.find()) {
            int lineNumber = Integer.parseInt(exceptionMatcher.group(1));
            moveToErrorLine(lineNumber);
        }
    }

    private void onProgramStart() {
        Platform.runLater(() -> output.getItems().clear());
    }

    private void onProgramEnd() {
        Platform.runLater(() -> {
            changeIndicatorState(false);
            runButton.setVisible(true);
        });
    }

    private void moveToErrorLine(int lineNumber) {
        Platform.runLater(() -> {
            codeArea.showParagraphAtTop(lineNumber - 1);
            codeArea.moveTo(lineNumber - 1, 0);
        });
    }

    private void generateKotlin(String sourceCode) {
        File file = new File("TempKotlin.kts");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(sourceCode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void changeIndicatorState(Boolean visible) {
        progressIndicator.setVisible(visible);
    }

}
