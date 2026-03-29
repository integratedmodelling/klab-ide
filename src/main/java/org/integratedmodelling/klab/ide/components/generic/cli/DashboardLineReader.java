package org.integratedmodelling.klab.ide.components.generic.cli;

import javafx.scene.control.TextField;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.terminal.MouseEvent;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DashboardLineReader implements LineReader {
    
    public interface PrintCallback {
        void onPrint(String text);
        void onPrintAbove(String text);
    }
    
    private final TextField textField;
    private final PrintCallback printCallback;
    
    public DashboardLineReader(TextField textField, PrintCallback printCallback) {
        this.textField = textField;
        this.printCallback = printCallback;
        
    }

    @Override
    public Map<String, KeyMap<Binding>> defaultKeyMaps() {
        return Map.of();
    }

    @Override
    public String readLine() throws UserInterruptException, EndOfFileException {
        return readLineImpl(null, null);
    }
    
    @Override
    public String readLine(Character mask) throws UserInterruptException, EndOfFileException {
        return readLineImpl(null, mask);
    }
    
    @Override
    public String readLine(String prompt) throws UserInterruptException, EndOfFileException {
        return readLineImpl(prompt, null);
    }
    
    @Override
    public String readLine(String prompt, Character mask) throws UserInterruptException, EndOfFileException {
        return readLineImpl(prompt, mask);
    }
    
    @Override
    public String readLine(String prompt, Character mask,
                           String buffer) throws UserInterruptException, EndOfFileException {
        textField.setText(buffer);
        return readLineImpl(prompt, mask);
    }
    
    @Override
    public String readLine(String prompt, String rightPrompt, Character mask,
                           String buffer) throws UserInterruptException, EndOfFileException {
        textField.setText(buffer);
        return readLineImpl(prompt, mask);
    }
    
    @Override
    public String readLine(String prompt, String rightPrompt, MaskingCallback maskingCallback,
                           String buffer) throws UserInterruptException, EndOfFileException {
        textField.setText(buffer);
        return readLineImpl(prompt, null);
    }
    
    private String readLineImpl(String prompt, Character mask) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        if (prompt != null) {
            printCallback.onPrint(prompt);
        }
        
        textField.setOnAction(e -> {
            String text = textField.getText();
            textField.clear();
            future.complete(text);
        });
        
        return future.join();
    }

    @Override
    public void printAbove(String str) {
        printCallback.onPrintAbove(str);
    }
    
    @Override
    public void printAbove(AttributedString str) {
        printCallback.onPrintAbove(str.toString());
    }

    @Override
    public boolean isReading() {
        return false;
    }

    @Override
    public LineReader variable(String name, Object value) {
        return null;
    }

    @Override
    public LineReader option(Option option, boolean value) {
        return null;
    }

    @Override
    public void callWidget(String name) {

    }

    @Override
    public Map<String, Object> getVariables() {
        return Map.of();
    }

    @Override
    public Object getVariable(String name) {
        return null;
    }

    @Override
    public void setVariable(String name, Object value) {

    }

    @Override
    public boolean isSet(Option option) {
        return false;
    }

    @Override
    public void setOpt(Option option) {

    }

    @Override
    public void unsetOpt(Option option) {

    }

    @Override
    public Terminal getTerminal() {
        return null;
    }

    @Override
    public Map<String, Widget> getWidgets() {
        return Map.of();
    }

    @Override
    public Map<String, Widget> getBuiltinWidgets() {
        return Map.of();
    }

    @Override
    public Buffer getBuffer() {
        return null;
    }

    @Override
    public String getAppName() {
        return "";
    }

    @Override
    public void runMacro(String macro) {

    }

    @Override
    public MouseEvent readMouseEvent() {
        return null;
    }

    @Override
    public History getHistory() {
        return null;
    }

    @Override
    public Parser getParser() {
        return null;
    }

    @Override
    public Highlighter getHighlighter() {
        return null;
    }

    @Override
    public Expander getExpander() {
        return null;
    }

    @Override
    public Map<String, KeyMap<Binding>> getKeyMaps() {
        return Map.of();
    }

    @Override
    public String getKeyMap() {
        return "";
    }

    @Override
    public boolean setKeyMap(String name) {
        return false;
    }

    @Override
    public KeyMap<Binding> getKeys() {
        return null;
    }

    @Override
    public ParsedLine getParsedLine() {
        return null;
    }

    @Override
    public String getSearchTerm() {
        return "";
    }

    @Override
    public RegionType getRegionActive() {
        return null;
    }

    @Override
    public int getRegionMark() {
        return 0;
    }

    @Override
    public void addCommandsInBuffer(Collection<String> commands) {

    }

    @Override
    public void editAndAddInBuffer(File file) throws Exception {

    }

    @Override
    public String getLastBinding() {
        return "";
    }

    @Override
    public String getTailTip() {
        return "";
    }

    @Override
    public void setTailTip(String tailTip) {

    }

    @Override
    public void setAutosuggestion(SuggestionType type) {

    }

    @Override
    public SuggestionType getAutosuggestion() {
        return null;
    }

    @Override
    public void zeroOut() {

    }
}
