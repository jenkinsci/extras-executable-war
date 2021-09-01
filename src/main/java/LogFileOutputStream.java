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
            Signal.handle(new Signal("ALRM"), signal -> {
                try {
                    reopen();
                } catch (IOException e) {
                    throw new Error(e); // failed to reopen
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
