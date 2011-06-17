/*
 * The MIT License
 *
 * Copyright (c) 2008, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.HeadlessException;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

/**
 * Hudson console GUI.
 * 
 * @author Kohsuke Kawaguchi
 */
class MainDialog extends JFrame {
    private final JTextArea textArea = new JTextArea();

    public MainDialog() throws HeadlessException, IOException {
        super("Jenkins Console");
        
        JScrollPane pane = new JScrollPane(textArea);
        pane.setMinimumSize(new Dimension(400,150));
        pane.setPreferredSize(new Dimension(400,150));
        add(pane);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationByPlatform(true);

        PipedOutputStream out = new PipedOutputStream();
        PrintStream pout = new PrintStream(out);
        System.setErr(pout);
        System.setOut(pout);

        // this thread sets the text to JTextArea
        final BufferedReader in = new BufferedReader(new InputStreamReader(new PipedInputStream(out)));
        new Thread() {
            public void run() {
                try {
                    while(true) {
                        String line;
                        while((line=in.readLine())!=null) {
                            final String text = line;
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    textArea.append(text+'\n');
                                    scrollDown();
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }.start();

        pack();
    }

    /**
     * Forces the scroll of text area.
     */
    private void scrollDown() {
        int pos = textArea.getDocument().getEndPosition().getOffset();
        textArea.getCaret().setDot(pos);
        textArea.requestFocus();
    }
}
