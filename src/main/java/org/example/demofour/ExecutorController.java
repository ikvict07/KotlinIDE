package org.example.demofour;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import org.example.demofour.executor.MyExecutor;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutorController implements Initializable {
    private static final String[] KEYWORDS = new String[]{
            "as", "as?", "break", "class", "continue", "do",
            "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true",
            "try", "typealias", "val", "var", "when", "while"
    };
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "[()]";
    private static final String BRACE_PATTERN = "[{}]";
    private static final String BRACKET_PATTERN = "[\\[\\]]";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String SEMICOLON_PATTERN = ";";
    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );
    public ListView<Label> output;
    public ProgressIndicator progressIndicator;
    @FXML
    private AnchorPane placeForCodeArea;
    private CodeArea codeArea;
    private String sourceCode;
    @FXML
    private Button runButton;

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static String getStyleClass(Matcher matcher) {
        String styleClass =
                matcher.group("KEYWORD") != null ? "keyword" :
                        matcher.group("PAREN") != null ? "paren" :
                                matcher.group("BRACE") != null ? "brace" :
                                        matcher.group("BRACKET") != null ? "bracket" :
                                                matcher.group("SEMICOLON") != null ? "semicolon" :
                                                        matcher.group("STRING") != null ? "string" :
                                                                matcher.group("COMMENT") != null ? "comment" :
                                                                        null; /* never happens */
        assert styleClass != null;
        return styleClass;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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
                    codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
                });

        for (int i = 0; i < 50; i++) {
            codeArea.appendText("\n");
        }

        runButton.setOnAction(event -> run());

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

    private void run() {
        sourceCode = codeArea.getText();
        generateKotlin(sourceCode);
        MyExecutor executor = new MyExecutor();
        changeIndicatorState(true);
        executor.execute(lines -> {
                    for (String line : lines.split("\n")) {
                        Label textField = new Label(line);
                        Platform.runLater(() -> {
                            textField.setOnMouseClicked(event -> {
                                Pattern errorMatcher = Pattern.compile(".*\\.kts:(\\d+):\\d+: .*");
                                Matcher lineMatcher = errorMatcher.matcher(textField.getText());
                                if (lineMatcher.find()) {
                                    int lineNumber = Integer.parseInt(lineMatcher.group(1));
                                    moveToErrorLine(lineNumber);
                                }
                                Pattern exceptionPattern = Pattern.compile(".*\\.kts:(\\d+).*");
                                Matcher exceptionMatcher = exceptionPattern.matcher(line);
                                System.out.println(line);
                                if (exceptionMatcher.find()) {
                                    int lineNumber = Integer.parseInt(exceptionMatcher.group(1));
                                    moveToErrorLine(lineNumber);
                                }
                            });
                            output.getItems().add(textField);
                        });

                        Pattern errorMatcher = Pattern.compile(".*\\.kts:(\\d+):\\d+: .*");
                        Matcher lineMatcher = errorMatcher.matcher(textField.getText());
                        if (lineMatcher.find()) {
                            textField.setStyle("-fx-text-fill: red;");
                        }

                        Pattern exceptionPattern = Pattern.compile(".*\\.kts:(\\d+).*");
                        Matcher exceptionMatcher = exceptionPattern.matcher(line);
                        System.out.println(line);
                        if (exceptionMatcher.find()) {
                            textField.setStyle("-fx-text-fill: red;");
                        }
                    }
                },
                output,
                () -> changeIndicatorState(false));
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
