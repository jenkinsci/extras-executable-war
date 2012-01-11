import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * {@link SimpleFormatter} that uses ANSI color escape sequence.
 *
 * @author Kohsuke Kawaguchi
 */
public class ColorFormatter extends SimpleFormatter {
    public String format(LogRecord record) {
        String body = super.format(record);
        int v = record.getLevel().intValue();
        if (v >= SEVERE)
            return "\u001B[31m" + body + "\u001B[0m";
        if (v >= WARNING)
            return "\u001B[33m" + body + "\u001B[0m";
        return body;
    }

    /**
     * Conservatively installs the color logger.
     *
     * If we don't run with JDK6, or if the stdin/stdout isn't console, back off.
     */
    public static void install() {
        try {
            if (System.class.getMethod("console",new Class[0]).invoke(null,new Object[0])==null)
                return;
        } catch (Throwable t) {
            return;
        }

        Handler[] handlers = Logger.getLogger("").getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            Handler h = handlers[i];
            if (h.getClass() == ConsoleHandler.class) {
                if (h.getFormatter().getClass() == SimpleFormatter.class)
                    h.setFormatter(new ColorFormatter());
            }
        }
    }

    private static final int SEVERE = Level.SEVERE.intValue();
    private static final int WARNING = Level.WARNING.intValue();
}
