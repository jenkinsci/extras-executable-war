import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/**
 * Launcher class for stand-alone execution of Hudson as
 * <tt>java -jar jenkins.war</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    private static boolean hasLogOption(String[] args) {
        for (int i = 0; i < args.length; i++)
            if(args[i].startsWith("--logfile="))
                return true;
        return false;
    }

    /**
     * Reads <tt>WEB-INF/classes/dependencies.txt and builds "groupId:artifactId" -> "version" map.
     */
    private static Map/*<String,String>*/ parseDependencyVersions() throws IOException {
        Map r = new HashMap();
        BufferedReader in = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream("WEB-INF/classes/dependencies.txt")));
        String line;
        while ((line=in.readLine())!=null) {
            line = line.trim();
            String[] tokens = line.split(":");
            if (tokens.length!=5)   continue;   // there should be 5 tuples group:artifact:type:version:scope
            r.put(tokens[0]+":"+tokens[1],tokens[3]);
        }
        return r;
    }

    public static void main(String[] args) throws Exception {
        try {
            _main(args);
        } catch (UnsupportedClassVersionError e) {
            System.err.println("Jenkins requires Java5 or later.");
            e.printStackTrace();
        }
    }
    
    private static void _main(String[] args) throws Exception {
        // if we need to daemonize, do it first
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--daemon")) {
                Map revisions = parseDependencyVersions();

                // load the daemonization code
                ClassLoader cl = new URLClassLoader(new URL[]{
                    extractFromJar("WEB-INF/lib/jna-"+revisions.get("net.java.dev.jna:jna")+".jar","jna","jar").toURI().toURL(),
                    extractFromJar("WEB-INF/lib/akuma-"+revisions.get("com.sun.akuma:akuma")+".jar","akuma","jar").toURI().toURL(),
                });
                Class $daemon = cl.loadClass("com.sun.akuma.Daemon");
                Object daemon = $daemon.newInstance();

                // tell the user that we'll be starting as a daemon.
                Method isDaemonized = $daemon.getMethod("isDaemonized", new Class[]{});
                if(!((Boolean)isDaemonized.invoke(daemon,new Object[0])).booleanValue()) {
                    System.out.println("Forking into background to run as a daemon.");
                    if(!hasLogOption(args))
                        System.out.println("Use --logfile to redirect output to a file");
                }

                Method m = $daemon.getMethod("all", new Class[]{boolean.class});
                m.invoke(daemon,new Object[]{Boolean.TRUE});
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

        // tell Hudson that Winstone doesn't support chunked encoding.
        if(System.getProperty("hudson.diyChunking")==null)
            System.setProperty("hudson.diyChunking","true");

        File me = whoAmI();
        System.out.println("Running from: " + me);
        System.setProperty("executable-war",me.getAbsolutePath());  // remember the location so that we can access it from within webapp

        // figure out the arguments
        List arguments = new ArrayList(Arrays.asList(args));
        arguments.add(0,"--warfile="+ me.getAbsolutePath());
        if(!hasWebRoot(arguments)) {
            // defaults to ~/.hudson/war since many users reported that cron job attempts to clean up
            // the contents in the temporary directory.
            final FileAndDescription describedHomeDir = getHomeDir();
            System.out.println("webroot: " + describedHomeDir.description);
            arguments.add("--webroot="+new File(describedHomeDir.file,"war"));
        }

        if(arguments.contains("--version")) {
            System.out.println(getVersion("?"));
            return;
        }

        // put winstone jar in a file system so that we can load jars from there
        File tmpJar = extractFromJar("winstone.jar","winstone","jar");

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Hudson
        // is deployed.
        File tempFile = File.createTempFile("dummy", "dummy");
        deleteContents(new File(tempFile.getParent(), "winstone/" + me.getName()));
        tempFile.delete();

        // locate the Winstone launcher
        ClassLoader cl = new URLClassLoader(new URL[]{tmpJar.toURI().toURL()});
        Class launcher = cl.loadClass("winstone.Launcher");
        Method mainMethod = launcher.getMethod("main", new Class[]{String[].class});

        // override the usage screen
        Field usage = launcher.getField("USAGE");
        usage.set(null,"Jenkins Continuous Integration Engine "+getVersion("")+"\n" +
                "Usage: java -jar jenkins.war [--option=value] [--option=value]\n" +
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
                "                              if neither --httpsCertificate nor --httpsKeyStore are specified,\n" +
                "                              https is run with one-time self-signed certificate.\n" +
                "   --httpsListenAddress     = set the https listening address. Default is all interfaces\n" +
                "   --httpsDoHostnameLookups = enable host name lookups on incoming https connections (true/false). Default is false\n" +
                "   --httpsKeyStore          = the location of the SSL KeyStore file.\n" +
                "   --httpsKeyStorePassword  = the password for the SSL KeyStore file. Default is null\n" +
                "   --httpsCertificate       = the location of the PEM-encoded SSL certificate file.\n" +
                "                              (the one that starts with '-----BEGIN CERTIFICATE-----')\n" +
                "                              must be used with --httpsPrivateKey.\n" +
                "   --httpsPrivateKey        = the location of the PEM-encoded SSL private key.\n" +
                "                              (the one that starts with '-----BEGIN RSA PRIVATE KEY-----')\n" +
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
                "   --mimeTypes=ARG          = define additional MIME type mappings. ARG would be EXT=MIMETYPE:EXT=MIMETYPE:...\n" +
                "                              (e.g., xls=application/vnd.ms-excel:wmf=application/x-msmetafile)\n" +
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

        // run
        mainMethod.invoke(null,new Object[]{arguments.toArray(new String[0])});
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
        // JNLP returns the URL where the jar was originally placed (like http://hudson.dev.java.net/...)
        // not the local cached file. So we need a rather round about approach to get to
        // the local file name.
        // There is no portable way to find where the locally cached copy
        // of hudson.war/jar is; JDK 6 is too smart. (See HUDSON-2326.)
        try {
            URL classFile = Main.class.getClassLoader().getResource("Main.class");
            JarFile jf = ((JarURLConnection) classFile.openConnection()).getJarFile();
            Field f = ZipFile.class.getDeclaredField("name");
            f.setAccessible(true);
            return new File((String) f.get(jf));
        } catch (Exception x) {
            System.err.println("ZipFile.name trick did not work, using fallback: " + x);
        }
        File myself = File.createTempFile("jenkins", ".jar");
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
    private static File extractFromJar(String resource, String fileName, String suffix) throws IOException {
        URL res = Main.class.getResource(resource);

        // put this jar in a file system so that we can load jars from there
        File tmp;
        try {
            tmp = File.createTempFile(fileName,suffix);
        } catch (IOException e) {
            String tmpdir = System.getProperty("java.io.tmpdir");
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
