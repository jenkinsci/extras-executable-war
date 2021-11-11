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
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Launches {@code hudson.war} from JNLP.
 *
 * @author Kohsuke Kawaguchi
 */
public class JNLPMain {
    public static void main(String[] args) throws Exception {
        // don't know if this is really necessary, but jnlp-agent benefited from this,
        // so I'm doing it here, too.
        try {
            System.setSecurityManager(null);
        } catch (SecurityException e) {
            // ignore and move on.
            // some user reported that this happens on their JVM: http://d.hatena.ne.jp/tueda_wolf/20080723
        }

        // we use to configure this in the JNLP file, but a recent change in the webstart makes
        // it very difficult to do this. See http://forums.sun.com/thread.jspa?threadID=5356707
        // and http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6653241
        if(System.getProperty(PROMINENT_WINDOWS_INSTALLER)==null)
            System.setProperty(PROMINENT_WINDOWS_INSTALLER,"true");

        boolean headlessMode = Boolean.getBoolean("hudson.webstart.headless");
        if (!headlessMode) {
            // launch GUI to display output
            setUILookAndFeel();
            new MainDialog().setVisible(true);
        }

        Main.main(args);
    }

    /**
     * Sets to the platform native look and feel.
     *
     * see http://javaalmanac.com/egs/javax.swing/LookFeelNative.html
     */
    public static void setUILookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (InstantiationException | ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException e) {
        }
    }

    private static final String PROMINENT_WINDOWS_INSTALLER = "hudson.lifecycle.WindowsInstallerLink.prominent";
}
