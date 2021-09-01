/*
 * The MIT License
 *
 * Copyright (c) 2008-2011, Sun Microsystems, Inc., Alan Harder, Jerome Lacoste, Kohsuke Kawaguchi,
 * bap2000, CloudBees, Inc.
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nonnull;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Launcher class for stand-alone execution of Jenkins as
 * <tt>java -jar jenkins.war</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    
    private static final Set<Integer> SUPPORTED_JAVA_VERSIONS =
            new HashSet<Integer>(Arrays.asList(8, 11));
    private static final Set<Integer> SUPPORTED_JAVA_CLASS_VERSIONS =
            new HashSet<Integer>(Arrays.asList(52, 55));
    private static final int MINIMUM_JAVA_CLASS_VERSION = 52;

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    /**
     * Sets custom session cookie name.
     * It may be used to prevent randomization of JSESSIONID cookies and issues like
     * <a href="https://issues.jenkins-ci.org/browse/JENKINS-25046">JENKINS-25046</a>.
     * @since TODO
     */
    private static final String JSESSIONID_COOKIE_NAME = 
            System.getProperty("executableWar.jetty.sessionIdCookieName");
    
    /**
     * Disables usage of the custom cookie names when starting the WAR file.
     * If the flag is specified, the session ID will be defined by the internal Jetty logic.
     * In such case it becomes configurable via 
     * <a href="http://www.eclipse.org/jetty/documentation/9.4.x/jetty-xml-config.html">Jetty XML Config file</a>>
     * or via system properties.
     * @since TODO
     */
    private static final boolean DISABLE_CUSTOM_JSESSIONID_COOKIE_NAME = 
            Boolean.getBoolean("executableWar.jetty.disableCustomSessionIdCookieName");

    /**
     * Flag to bypass the Java version check when starting.
     */
    private static final String ENABLE_FUTURE_JAVA_CLI_SWITCH = "--enable-future-java";

    public static void main(String[] args) throws Exception {
        try {
            String v = System.getProperty("java.class.version");
            if (v!=null) {
                String classVersionString = v.split("\\.")[0];
                try {
                    int javaVersion = Integer.parseInt(classVersionString);
                    verifyJavaVersion(javaVersion, isFutureJavaEnabled(args));
                } catch (NumberFormatException e) {
                    // err on the safe side and keep on going
                    LOGGER.log(Level.WARNING, "Failed to parse java.class.version: {0}. Will continue execution", v);
                }
            }

            ColorFormatter.install();

            _main(args);
        } catch (UnsupportedClassVersionError e) {
            System.err.println(String.format(
                    "Jenkins requires Java versions %s but you are running with Java %s from %s",
                    SUPPORTED_JAVA_VERSIONS, System.getProperty("java.specification.version"), System.getProperty("java.home"))
            );
            e.printStackTrace();
        }
    }

    /*package*/ static void verifyJavaVersion(int javaClassVersion, boolean enableFutureJava)
            throws Error {
        final String displayVersion = String.format("%d.0", javaClassVersion);
        if (SUPPORTED_JAVA_CLASS_VERSIONS.contains(javaClassVersion)) {
            // Fine
        } else if (javaClassVersion > MINIMUM_JAVA_CLASS_VERSION) {
            if (enableFutureJava) {
                LOGGER.log(Level.WARNING,
                        String.format("Running with Java class version %s which is not in the list of supported versions: %s. " +
                                        "Argument %s is set, so will continue. " +
                                        "See https://jenkins.io/redirect/java-support/",
                                javaClassVersion, SUPPORTED_JAVA_CLASS_VERSIONS, ENABLE_FUTURE_JAVA_CLI_SWITCH));
            } else {
                Error error = new UnsupportedClassVersionError(displayVersion);
                LOGGER.log(Level.SEVERE, String.format("Running with Java class version %s which is not in the list of supported versions: %s. " +
                                "Run with the " + ENABLE_FUTURE_JAVA_CLI_SWITCH + " flag to enable such behavior. " +
                                "See https://jenkins.io/redirect/java-support/",
                        javaClassVersion, SUPPORTED_JAVA_CLASS_VERSIONS), error);
                throw error;
            }
        } else {
            Error error = new UnsupportedClassVersionError(displayVersion);
            LOGGER.log(Level.SEVERE,
                    String.format("Running with Java class version %s, which is older than the Minimum required version %s. " +
                                    "See https://jenkins.io/redirect/java-support/",
                            javaClassVersion, MINIMUM_JAVA_CLASS_VERSION), error);
            throw error;
        }
    }

    /**
     * Returns true if the Java runtime version check should not be done, and any version allowed.
     * @see #ENABLE_FUTURE_JAVA_CLI_SWITCH
     */
    private static boolean isFutureJavaEnabled(String[] args) {
        return hasArgument(ENABLE_FUTURE_JAVA_CLI_SWITCH, args) || Boolean.parseBoolean(System.getenv("JENKINS_ENABLE_FUTURE_JAVA"));
    }

    //TODO: Rework everything to use List
    private static boolean hasArgument(@Nonnull String argument, @Nonnull String[] args) {
        for (String arg : args) {
            if (argument.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void _main(String[] args) throws Exception {
        //Allows to pass arguments through stdin to "hide" sensitive parameters like httpsKeyStorePassword
        //to achieve this use --paramsFromStdIn
        if (hasArgument("--paramsFromStdIn", args)) {
            System.out.println("--paramsFromStdIn detected. Parameters are going to be read from stdin. Other parameters passed directly will be ignored.");
            String argsInStdIn = readStringNonBlocking(System.in,131072).trim();
            args = argsInStdIn.split(" +");
        }
        // If someone just wants to know the version, print it out as soon as possible, with no extraneous file or webroot info.
        // This makes it easier to grab the version from a script
        final List<String> arguments = new ArrayList(Arrays.asList(args));
        if (arguments.contains("--version")) {
            System.out.println(getVersion("?"));
            return;
        }

        File extractedFilesFolder = null;
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--extractedFilesFolder=")) {
                extractedFilesFolder = new File(args[i].substring("--extractedFilesFolder=".length()));
                if (!extractedFilesFolder.isDirectory()) {
                    System.err.println("The extractedFilesFolder value is not a directory. Ignoring.");
                    extractedFilesFolder = null;
                }
            }
        }

        // if the output should be redirect to a file, do it now
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--logfile=")) {
                PrintStream ps = createLogFileStream(new File(args[i].substring("--logfile=".length())));
                System.setOut(ps);
                System.setErr(ps);
                // don't let winstone see this
                List _args = new ArrayList(Arrays.asList(args));
                _args.remove(i);
                args = (String[]) _args.toArray(new String[_args.size()]);
                break;
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--pluginroot=")) {
                System.setProperty("hudson.PluginManager.workDir",
                        new File(args[i].substring("--pluginroot=".length())).getAbsolutePath());
                // if specified multiple times, the first one wins
                break;
            }
        }


        // this is so that JFreeChart can work nicely even if we are launched as a daemon
        System.setProperty("java.awt.headless","true");

        File me = whoAmI(extractedFilesFolder);
        System.out.println("Running from: " + me);
        System.setProperty("executable-war",me.getAbsolutePath());  // remember the location so that we can access it from within webapp

        // figure out the arguments
        trimOffOurOptions(arguments);
        arguments.add(0,"--warfile="+ me.getAbsolutePath());
        if(!hasOption(arguments, "--webroot=")) {
            // defaults to ~/.jenkins/war since many users reported that cron job attempts to clean up
            // the contents in the temporary directory.
            final FileAndDescription describedHomeDir = getHomeDir();
            System.out.println("webroot: " + describedHomeDir.description);
            arguments.add("--webroot="+new File(describedHomeDir.file,"war"));
        }

        //only do a cleanup if you set the extractedFilesFolder property.
        if(extractedFilesFolder != null) {
            deleteContentsFromFolder(extractedFilesFolder, "winstone.*\\.jar");
        }

        // put winstone jar in a file system so that we can load jars from there
        File tmpJar = extractFromJar("winstone.jar","winstone",".jar", extractedFilesFolder);
        tmpJar.deleteOnExit();

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Jenkins
        // is deployed.
        File tempFile = File.createTempFile("dummy", "dummy");
        deleteWinstoneTempContents(new File(tempFile.getParent(), "winstone/" + me.getName()));
        if (!tempFile.delete()) {
            LOGGER.log(Level.WARNING, "Failed to delete the temporary file {0}", tempFile);
        }
                
        // locate the Winstone launcher
        ClassLoader cl = new URLClassLoader(new URL[]{tmpJar.toURI().toURL()});
        Class launcher = cl.loadClass("winstone.Launcher");
        Method mainMethod = launcher.getMethod("main", new Class[]{String[].class});

        // override the usage screen
        Field usage = launcher.getField("USAGE");
        usage.set(null,"Jenkins Automation Server Engine "+getVersion("")+"\n" +
                "Usage: java -jar jenkins.war [--option=value] [--option=value]\n" +
                "\n" +
                "Options:\n" +
                "   --webroot                = folder where the WAR file is expanded into. Default is ${JENKINS_HOME}/war\n" +
                "   --pluginroot             = folder where the plugin archives are expanded into. Default is ${JENKINS_HOME}/plugins\n" +
                "                              (NOTE: this option does not change the directory where the plugin archives are stored)\n" +
                "   --extractedFilesFolder   = folder where extracted files are to be located. Default is the temp folder\n" +
                "   --logfile                = redirect log messages to this file\n" +
                "   " + ENABLE_FUTURE_JAVA_CLI_SWITCH + "     = allows running with new Java versions which are not fully supported (class version " + MINIMUM_JAVA_CLASS_VERSION + " and above)\n" +
                "{OPTIONS}");

        
        if (!DISABLE_CUSTOM_JSESSIONID_COOKIE_NAME) {
            /*
             Set an unique cookie name.

             As can be seen in discussions like http://stackoverflow.com/questions/1146112/jsessionid-collision-between-two-servers-on-same-ip-but-different-ports
             and http://stackoverflow.com/questions/1612177/are-http-cookies-port-specific, RFC 2965 says
             cookies from one port of one host may be sent to a different port of the same host.
             This means if someone runs multiple Jenkins on different ports of the same host,
             their sessions get mixed up.

             To fix the problem, use unique session cookie name.

             This change breaks the cluster mode of Winstone, as all nodes in the cluster must share the same session cookie name.
             Jenkins doesn't support clustered operation anyway, so we need to do this here, and not in Winstone.
            */
            try {
                Field f = cl.loadClass("winstone.WinstoneSession").getField("SESSION_COOKIE_NAME");
                f.setAccessible(true);
                if (JSESSIONID_COOKIE_NAME != null) {
                    // Use the user-defined cookie name
                    f.set(null, JSESSIONID_COOKIE_NAME);
                } else {
                    // Randomize session names by default to prevent collisions when running multiple Jenkins instances on the same host.
                    f.set(null,"JSESSIONID."+UUID.randomUUID().toString().replace("-","").substring(0,8));
                }
            } catch (ClassNotFoundException e) {
                // early versions of Winstone 2.x didn't have this
            }
        }

        // run
        Thread.currentThread().setContextClassLoader( cl );
        mainMethod.invoke(null,new Object[]{arguments.toArray(new String[0])});
    }

    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "--logfile relies on the default encoding, fine")
    private static PrintStream createLogFileStream(File file) throws IOException {
        LogFileOutputStream los = new LogFileOutputStream(file);
        return new PrintStream(los);
    }

    //TODO: Get rid of FB warning after updating to Java 7
    /**
     * reads up to maxRead bytes from InputStream if available into a String
     *
     * @param in input stream to be read
     * @param maxToRead maximum number of bytes to read from the in
     * @return a String read from in
     * @throws IOException when reading in caused it
     */
    @SuppressFBWarnings(value = {"DM_DEFAULT_ENCODING", "RR_NOT_CHECKED"}, justification = "Legacy behavior, We expect less input than maxToRead")
    private static String readStringNonBlocking(InputStream in, int maxToRead) throws IOException {
        byte [] buffer = new byte[Math.min(in.available(), maxToRead)];
        in.read(buffer);
        return new String(buffer);
    }
    private static void trimOffOurOptions(List arguments) {
        for (Iterator itr = arguments.iterator(); itr.hasNext(); ) {
            String arg = (String) itr.next();
            if (arg.startsWith("--logfile") || arg.startsWith("--extractedFilesFolder")
                    || arg.startsWith("--pluginroot") || arg.startsWith(ENABLE_FUTURE_JAVA_CLI_SWITCH))
                itr.remove();
        }
    }

    /**
     * Figures out the version from the manifest.
     */
    private static String getVersion(String fallback) throws IOException {
        Enumeration manifests = Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            URL res = (URL)manifests.nextElement();
            Manifest manifest = new Manifest(res.openStream());
            String v = manifest.getMainAttributes().getValue("Jenkins-Version");
            if(v!=null)
                return v;
        }
        return fallback;
    }

    private static boolean hasOption(List args, String prefix) {
        for (Iterator itr = args.iterator(); itr.hasNext();) {
            String s = (String) itr.next();
            if (s.startsWith(prefix))
                return true;
        }
        return false;
    }

    /**
     * Figures out the URL of <tt>jenkins.war</tt>.
     */
    public static File whoAmI(File directory) throws IOException, URISyntaxException {
        // JNLP returns the URL where the jar was originally placed (like http://jenkins-ci.org/...)
        // not the local cached file. So we need a rather round about approach to get to
        // the local file name.
        // There is no portable way to find where the locally cached copy
        // of jenkins.war/jar is; JDK 6 is too smart. (See JENKINS-2326.)
        try {
            URL classFile = Main.class.getClassLoader().getResource("Main.class");
            JarFile jf = ((JarURLConnection) classFile.openConnection()).getJarFile();
            return new File(jf.getName());
        } catch (Exception x) {
            System.err.println("ZipFile.name trick did not work, using fallback: " + x);
        }
        File myself = File.createTempFile("jenkins", ".jar", directory);
        myself.deleteOnExit();
        InputStream is = Main.class.getProtectionDomain().getCodeSource().getLocation().openStream();
        try {
            OutputStream os = new FileOutputStream(myself);
            try {
                copyStream(is, os);
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
        return myself;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
    }

    /**
     * Extract a resource from jar, mark it for deletion upon exit, and return its location.
     */
    private static File extractFromJar(String resource, String fileName, String suffix, File directory) throws IOException {
        URL res = Main.class.getResource(resource);
        if (res==null)
            throw new IOException("Unable to find the resource: "+resource); 

        // put this jar in a file system so that we can load jars from there
        File tmp;
        try {
            tmp = File.createTempFile(fileName,suffix,directory);
        } catch (IOException e) {
            String tmpdir = (directory == null) ? System.getProperty("java.io.tmpdir") : directory.getAbsolutePath();
            IOException x = new IOException("Jenkins has failed to create a temporary file in " + tmpdir);
            x.initCause(e);
            throw x;
        }
        InputStream is = res.openStream();
        try {
            OutputStream os = new FileOutputStream(tmp);
            try {
                copyStream(is,os);
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
        tmp.deleteOnExit();
        return tmp;
    }

    /**
     * Search contents to delete in a folder that match with some patterns.
     * @param folder folder where the contents are.
     * @param patterns patterns that identifies the contents to search.
     * @throws IOException in case of error deleting contents.
     */
    private static void deleteContentsFromFolder(File folder, final String...patterns) throws IOException {
        File[]  files = folder.listFiles();

        if(files != null){
            for (File file : files) {
                for (String pattern : patterns) {
                    if(file.getName().matches(pattern)){
                        LOGGER.log(Level.FINE, "Deleting the temporary file {0}", file);
                        deleteWinstoneTempContents(file);
                    }
                }
            }
        }
    }

    private static void deleteWinstoneTempContents(File file) throws IOException {
        if (!file.exists()) {
            LOGGER.log(Level.FINEST, "No file found at {0}, nothing to delete.", file);
            return;
        }
        if(file.isDirectory()) {
            File[] files = file.listFiles();
            if(files!=null) {// be defensive
                for (int i = 0; i < files.length; i++)
                    deleteWinstoneTempContents(files[i]);
            }
        }
        if (!file.delete()) {
            LOGGER.log(Level.WARNING, "Failed to delete the temporary Winstone file {0}", file);
        }
    }

    /** Add some metadata to a File, allowing to trace setup issues */
    private static class FileAndDescription {
        File file;
        String description;
        public FileAndDescription(File file,String description) {
            this.file = file;
            this.description = description;
        }
    }

    /**
     * Determines the home directory for Jenkins.
     *
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     *
     * @return the File alongside with some description to help the user troubleshoot issues
     */
    private static FileAndDescription getHomeDir() {
        // check JNDI for the home directory first
        for (int i = 0; i < HOME_NAMES.length; i++) {
            String name = HOME_NAMES[i];
            try {
                InitialContext iniCtxt = new InitialContext();
                Context env = (Context) iniCtxt.lookup("java:comp/env");
                String value = (String) env.lookup(name);
                if (value != null && value.trim().length() > 0)
                    return new FileAndDescription(new File(value.trim()), "JNDI/java:comp/env/" + name);
                // look at one more place. See issue #1314
                value = (String) iniCtxt.lookup(name);
                if (value != null && value.trim().length() > 0)
                    return new FileAndDescription(new File(value.trim()), "JNDI/" + name);
            } catch (NamingException e) {
                // ignore
            }
        }

        // next the system property
        for (int i = 0; i < HOME_NAMES.length; i++) {
            String name = HOME_NAMES[i];
            String sysProp = System.getProperty(name);
            if(sysProp!=null)
                return new FileAndDescription(new File(sysProp.trim()),"System.getProperty(\""+name+"\")");
        }

        // look at the env var next
        try {
            for (int i = 0; i < HOME_NAMES.length; i++) {
                String name = HOME_NAMES[i];
                String env = System.getenv(name);
                if(env!=null)
                    return new FileAndDescription(new File(env.trim()).getAbsoluteFile(),"EnvVars.masterEnvVars.get(\""+name+"\")");
            }
        } catch (Throwable e) {
            // this code fails when run on JDK1.4
        }

        // otherwise pick a place by ourselves

/* ServletContext not available yet
        String root = event.getServletContext().getRealPath("/WEB-INF/workspace");
        if(root!=null) {
            File ws = new File(root.trim());
            if(ws.exists())
                // Hudson <1.42 used to prefer this before ~/.hudson, so
                // check the existence and if it's there, use it.
                // otherwise if this is a new installation, prefer ~/.hudson
                return new FileAndDescription(ws,"getServletContext().getRealPath(\"/WEB-INF/workspace\")");
        }
*/

        // if for some reason we can't put it within the webapp, use home directory.
        File legacyHome = new File(new File(System.getProperty("user.home")),".hudson");
        if (legacyHome.exists()) {
            return new FileAndDescription(legacyHome,"$user.home/.hudson"); // before rename, this is where it was stored
        }

        File newHome = new File(new File(System.getProperty("user.home")),".jenkins");
        return new FileAndDescription(newHome,"$user.home/.jenkins");
    }

    private static final String[] HOME_NAMES = {"JENKINS_HOME","HUDSON_HOME"};
}
