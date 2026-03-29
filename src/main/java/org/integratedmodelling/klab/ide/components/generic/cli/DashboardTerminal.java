package org.integratedmodelling.klab.ide.components.generic.cli;

import org.jline.terminal.*;
import org.jline.utils.ColorPalette;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class DashboardTerminal implements Terminal {
    @Override
    public String getName() {
        return "";
    }

    @Override
    public SignalHandler handle(Signal signal, SignalHandler handler) {
        return null;
    }

    @Override
    public void raise(Signal signal) {

    }

    @Override
    public NonBlockingReader reader() {
        return null;
    }

    @Override
    public PrintWriter writer() {
        return null;
    }

    @Override
    public Charset encoding() {
        return null;
    }

    @Override
    public InputStream input() {
        return null;
    }

    @Override
    public OutputStream output() {
        return null;
    }

    @Override
    public boolean canPauseResume() {
        return false;
    }

    @Override
    public void pause() {

    }

    @Override
    public void pause(boolean wait) throws InterruptedException {

    }

    @Override
    public void resume() {

    }

    @Override
    public boolean paused() {
        return false;
    }

    @Override
    public Attributes enterRawMode() {
        return null;
    }

    @Override
    public boolean echo() {
        return false;
    }

    @Override
    public boolean echo(boolean echo) {
        return false;
    }

    @Override
    public Attributes getAttributes() {
        return null;
    }

    @Override
    public void setAttributes(Attributes attr) {

    }

    @Override
    public Size getSize() {
        return null;
    }

    @Override
    public void setSize(Size size) {

    }

    @Override
    public void flush() {

    }

    @Override
    public String getType() {
        return "";
    }

    @Override
    public boolean puts(InfoCmp.Capability capability, Object... params) {
        return false;
    }

    @Override
    public boolean getBooleanCapability(InfoCmp.Capability capability) {
        return false;
    }

    @Override
    public Integer getNumericCapability(InfoCmp.Capability capability) {
        return 0;
    }

    @Override
    public String getStringCapability(InfoCmp.Capability capability) {
        return "";
    }

    @Override
    public Cursor getCursorPosition(IntConsumer discarded) {
        return null;
    }

    @Override
    public boolean hasMouseSupport() {
        return false;
    }

    @Override
    public boolean trackMouse(MouseTracking tracking) {
        return false;
    }

    @Override
    public MouseEvent readMouseEvent() {
        return null;
    }

    @Override
    public MouseEvent readMouseEvent(IntSupplier reader) {
        return null;
    }

    @Override
    public boolean hasFocusSupport() {
        return false;
    }

    @Override
    public boolean trackFocus(boolean tracking) {
        return false;
    }

    @Override
    public ColorPalette getPalette() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
