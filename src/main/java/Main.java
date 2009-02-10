import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.JarURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.jar.Manifest;

/**
 * Launcher class for stand-alone execution of Hudson as
 * <tt>java -jar hudson.war</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // if we need to daemonize, do it first
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--daemon")) {
                // load the daemonization code
                ClassLoader cl = new URLClassLoader(new URL[]{
                    extractFromJar("WEB-INF/lib/jna-3.0.9.jar","jna","jar").toURI().toURL(),
                    extractFromJar("WEB-INF/lib/akuma-1.1.jar","akuma","jar").toURI().toURL(),
                });
                Class daemon = cl.loadClass("com.sun.akuma.Daemon");
                Method m = daemon.getMethod("all", new Class[]{boolean.class});
                m.invoke(daemon.newInstance(),new Object[]{Boolean.TRUE});
            }
        }


        // if the output should be redirect to a file, do it now
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--logfile=")) {
                LogFileOutputStream los = new LogFileOutputStream(new File(args[i].substring("--logfile=".length())));
                PrintStream ps = new PrintStream(los);
                System.setOut(ps);
                System.setErr(ps);
                // don't let winstone see this
                List _args = new ArrayList(Arrays.asList(args));
                _args.remove(i);
                args = (String[]) _args.toArray(new String[_args.size()]);
                break;
            }
        }

        // this is so that JFreeChart can work nicely even if we are launched as a daemon
        System.setProperty("java.awt.headless","true");

        File me = whoAmI();
        System.setProperty("executable-war",me.getAbsolutePath());  // remember the location so that we can access it from within webapp

        // put winstone jar in a file system so that we can load jars from there
        File tmpJar = extractFromJar("winstone.jar","winstone","jar");

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Hudson
        // is deployed.
        File tempFile = File.createTempFile("dummy", "dummy");
        deleteContents(new File(tempFile.getParent(), "winstone/" + me.getName()));
        tempFile.delete();

        // locate the Winstone launcher
        ClassLoader cl = new URLClassLoader(new URL[]{tmpJar.toURL()});
        Class launcher = cl.loadClass("winstone.Launcher");
        Method mainMethod = launcher.getMethod("main", new Class[]{String[].class});

        // figure out the arguments
        List arguments = new ArrayList(Arrays.asList(args));
        arguments.add(0,"--warfile="+ me.getAbsolutePath());
        if(!hasWebRoot(arguments))
            // defaults to ~/.hudson/war since many users reported that cron job attempts to clean up
            // the contents in the temporary directory.
            arguments.add("--webroot="+new File(getHomeDir(),"war"));

        // override the usage screen
        Field usage = launcher.getField("USAGE");
        usage.set(null,"Hudson Continuous Integration Engine "+getVersion()+"\n" +
                "Usage: java -jar hudson.war [--option=value] [--option=value]\n" +
                "\n" +
                "Options:\n" +
                "   --daemon                 = fork into background and run as daemon (Unix only)\n" +
                "   --config                 = load configuration properties from here. Default is ./winstone.properties\n" +
                "   --prefix                 = add this prefix to all URLs (eg http://localhost:8080/prefix/resource). Default is none\n" +
                "   --commonLibFolder        = folder for additional jar files. Default is ./lib\n" +
                "   \n" +
                "   --logfile                = redirect log messages to this file\n" +
                "   --logThrowingLineNo      = show the line no that logged the message (slow). Default is false\n" +
                "   --logThrowingThread      = show the thread that logged the message. Default is false\n" +
                "   --debug                  = set the level of debug msgs (1-9). Default is 5 (INFO level)\n" +
                "\n" +
                "   --httpPort               = set the http listening port. -1 to disable, Default is 8080\n" +
                "   --httpListenAddress      = set the http listening address. Default is all interfaces\n" +
                "   --httpDoHostnameLookups  = enable host name lookups on incoming http connections (true/false). Default is false\n" +
                "   --httpsPort              = set the https listening port. -1 to disable, Default is disabled\n" +
                "   --httpsListenAddress     = set the https listening address. Default is all interfaces\n" +
                "   --httpsDoHostnameLookups = enable host name lookups on incoming https connections (true/false). Default is false\n" +
                "   --httpsKeyStore          = the location of the SSL KeyStore file. Default is ./winstone.ks\n" +
                "   --httpsKeyStorePassword  = the password for the SSL KeyStore file. Default is null\n" +
                "   --httpsKeyManagerType    = the SSL KeyManagerFactory type (eg SunX509, IbmX509). Default is SunX509\n" +
                "   --ajp13Port              = set the ajp13 listening port. -1 to disable, Default is 8009\n" +
                "   --ajp13ListenAddress     = set the ajp13 listening address. Default is all interfaces\n" +
                "   --controlPort            = set the shutdown/control port. -1 to disable, Default disabled\n" +
                "   \n" +
                "   --handlerCountStartup    = set the no of worker threads to spawn at startup. Default is 5\n" +
                "   --handlerCountMax        = set the max no of worker threads to allow. Default is 300\n" +
                "   --handlerCountMaxIdle    = set the max no of idle worker threads to allow. Default is 50\n" +
                "   \n" +
                "   --simulateModUniqueId    = simulate the apache mod_unique_id function. Default is false\n" +
                "   --useSavedSessions       = enables session persistence (true/false). Default is false\n" +
                "   --usage / --help         = show this message\n" +
                "   --version                = show the version and quit\n" +
                "   \n" +
                "Security options:\n" +
                "   --realmClassName               = Set the realm class to use for user authentication. Defaults to ArgumentsRealm class\n" +
                "   \n" +
                "   --argumentsRealm.passwd.<user> = Password for user <user>. Only valid for the ArgumentsRealm realm class\n" +
                "   --argumentsRealm.roles.<user>  = Roles for user <user> (comma separated). Only valid for the ArgumentsRealm realm class\n" +
                "   \n" +
                "   --fileRealm.configFile         = File containing users/passwds/roles. Only valid for the FileRealm realm class\n" +
                "   \n" +
                "Access logging:\n" +
                "   --accessLoggerClassName        = Set the access logger class to use for user authentication. Defaults to disabled\n" +
                "   --simpleAccessLogger.format    = The log format to use. Supports combined/common/resin/custom (SimpleAccessLogger only)\n" +
                "   --simpleAccessLogger.file      = The location pattern for the log file(SimpleAccessLogger only)");

        if(arguments.contains("--version")) {
            System.out.println(getVersion());
            return;
        }

        // run
        mainMethod.invoke(null,new Object[]{arguments.toArray(new String[0])});
    }

    /**
     * Figures out the version from the manifest.
     */
    private static String getVersion() throws IOException {
        URL res = Main.class.getResource("/META-INF/MANIFEST.MF");
        if(res!=null) {
            Manifest manifest = new Manifest(res.openStream());
            String v = manifest.getMainAttributes().getValue("Implementation-Version");
            if(v!=null)
                return v;
        }
        return "?";
    }

    private static boolean hasWebRoot(List arguments) {
        for (Iterator itr = arguments.iterator(); itr.hasNext();) {
            String s = (String) itr.next();
            if(s.startsWith("--webroot="))
                return true;
        }
        return false;
    }

    /**
     * Figures out the URL of <tt>hudson.war</tt>.
     */
    public static File whoAmI() throws IOException, URISyntaxException {
        URL classFile = Main.class.getClassLoader().getResource("Main.class");

        // JNLP returns the URL where the jar was originally placed (like http://hudson.dev.java.net/...)
        // not the local cached file. So we need a rather round about approach to get to
        // the local file name.
        return new File(((JarURLConnection)classFile.openConnection()).getJarFile().getName());
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
        in.close();
        out.close();
    }

    /**
     * Extract a resource from jar, mark it for deletion upon exit, and return its location.
     */
    private static File extractFromJar(String resource, String fileName, String suffix) throws IOException {
        URL res = Main.class.getResource(resource);

        // put this jar in a file system so that we can load jars from there
        File tmp;
        try {
            tmp = File.createTempFile(fileName,suffix);
        } catch (IOException e) {
            String tmpdir = System.getProperty("java.io.tmpdir");
            IOException x = new IOException("Hudson has failed to create a temporary file in " + tmpdir);
            x.initCause(e);
            throw x;
        }
        copyStream(res.openStream(), new FileOutputStream(tmp));
        tmp.deleteOnExit();
        return tmp;
    }

    private static void deleteContents(File file) throws IOException {
        if(file.isDirectory()) {
            File[] files = file.listFiles();
            if(files!=null) {// be defensive
                for (int i = 0; i < files.length; i++)
                    deleteContents(files[i]);
            }
        }
        file.delete();
    }

    /**
     * Determines the home directory for Hudson.
     *
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     */
    private static File getHomeDir() {
        // check JNDI for the home directory first
        try {
            InitialContext iniCtxt = new InitialContext();
            Context env = (Context) iniCtxt.lookup("java:comp/env");
            String value = (String) env.lookup("HUDSON_HOME");
            if(value!=null && value.trim().length()>0)
                return new File(value.trim());
            // look at one more place. See issue #1314
            value = (String) iniCtxt.lookup("HUDSON_HOME");
            if(value!=null && value.trim().length()>0)
                return new File(value.trim());
        } catch (NamingException e) {
            // ignore
        }

        // finally check the system property
        String sysProp = System.getProperty("HUDSON_HOME");
        if(sysProp!=null)
            return new File(sysProp.trim());

        // look at the env var next
        try {
            String env = System.getenv("HUDSON_HOME");
            if(env!=null)
            return new File(env.trim()).getAbsoluteFile();
        } catch (Throwable _) {
            // when this code runs on JDK1.4, this method fails
            // fall through to the next method
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
                return ws;
        }
*/

        // if for some reason we can't put it within the webapp, use home directory.
        return new File(new File(System.getProperty("user.home")),".hudson");
    }
}
