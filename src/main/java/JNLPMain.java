import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Launches <tt>hudson.war</tt> from JNLP.
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
        } catch (InstantiationException e) {
        } catch (ClassNotFoundException e) {
        } catch (UnsupportedLookAndFeelException e) {
        } catch (IllegalAccessException e) {
        }
    }

    private static final String PROMINENT_WINDOWS_INSTALLER = "hudson.lifecycle.WindowsInstallerLink.prominent";
}
