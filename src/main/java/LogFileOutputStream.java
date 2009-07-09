import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} that writes to a log file.
 *
 * <p>
 * Unlike the plain {@link FileOutputStream}, this implementation
 * listens to SIGALRM and reopens the log file. This behavior is
 * necessary for allowing log rotations to happen smoothly.
 *
 * <p>
 * Because the reopen operation needs to happen atomically,
 * write operations are synchronized.
 *
 * @author Kohsuke Kawaguchi
 */
final class LogFileOutputStream extends FilterOutputStream {
    /**
     * This is where we are writing.
     */
    private final File file;

    LogFileOutputStream(File file) throws FileNotFoundException {
        super(null);
        this.file = file;
        out = new FileOutputStream(file,true);

        if(File.pathSeparatorChar==':') {
            Signal.handle(new Signal("ALRM"),new SignalHandler() {
                public void handle(Signal signal) {
                    try {
                        reopen();
                    } catch (IOException e) {
                        throw new Error(e); // failed to reopen
                    }
                }
            });
        }
    }

    public synchronized void reopen() throws IOException {
        out.close();
        out = NULL; // in case reopen fails, initialize with NULL first
        out = new FileOutputStream(file,true);
    }

    public synchronized void write(byte[] b) throws IOException {
        out.write(b);
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public synchronized void flush() throws IOException {
        out.flush();
    }

    public synchronized void close() throws IOException {
        out.close();
    }

    public synchronized void write(int b) throws IOException {
        out.write(b);
    }

    public String toString() {
        return getClass().getName()+" -> "+file;
    }

    /**
     * /dev/null
     */
    private static final OutputStream NULL = new OutputStream() {
        public void write(int b) throws IOException {
            // noop
        }

        public void write(byte[] b, int off, int len) throws IOException {
            // noop
        }
    };
}
