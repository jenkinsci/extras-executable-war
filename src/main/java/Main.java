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
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/**
 * Launcher class for stand-alone execution of Jenkins as
 * <tt>java -jar jenkins.war</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
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
            String v = System.getProperty("java.class.version");
            if (v!=null) {
                try {
                    if (Float.parseFloat(v)<51.0f)
                        throw new UnsupportedClassVersionError(v);
                } catch (NumberFormatException e) {
                    // err on the safe side and keep on going
                }
            }

            ColorFormatter.install();

            _main(args);
        } catch (UnsupportedClassVersionError e) {
            System.err.println("Jenkins requires Java7 or later, but you are running "+
                System.getProperty("java.runtime.version")+" from "+System.getProperty("java.home"));
            e.printStackTrace();
        }
    }

    private static void _main(String[] args) throws Exception {
        // If someone just wants to know the version, print it out as soon as possible, with no extraneous file or webroot info.
        // This makes it easier to grab the version from a script
        final List arguments = new ArrayList(Arrays.asList(args));
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

        // if we need to daemonize, do it first
        for (int i = 0; i < args.length; i++) {
            if(args[i].startsWith("--daemon")) {
                Map revisions = parseDependencyVersions();

                // load the daemonization code
                ClassLoader cl = new URLClassLoader(new URL[]{
                    extractFromJar("WEB-INF/lib/jna-"+getVersion(revisions, "net.java.dev.jna", "jna") +".jar","jna","jar", extractedFilesFolder).toURI().toURL(),
                    extractFromJar("WEB-INF/lib/akuma-"+getVersion(revisions,"org.kohsuke","akuma")+".jar","akuma","jar", extractedFilesFolder).toURI().toURL(),
                });
                Class $daemon = cl.loadClass("com.sun.akuma.Daemon");
                Object daemon = $daemon.newInstance();

                // tell the user that we'll be starting as a daemon.
                Method isDaemonized = $daemon.getMethod("isDaemonized", new Class[]{});
                if(!((Boolean)isDaemonized.invoke(daemon,new Object[0])).booleanValue()) {
                    System.out.println("Forking into background to run as a daemon.");
                    if(!hasOption(arguments, "--logfile="))
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

        // put winstone jar in a file system so that we can load jars from there
        File tmpJar = extractFromJar("winstone.jar","winstone",".jar", extractedFilesFolder);
        tmpJar.deleteOnExit();

        // clean up any previously extracted copy, since
        // winstone doesn't do so and that causes problems when newer version of Jenkins
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
                "   --webroot                = folder where the WAR file is expanded into. Default is ${JENKINS_HOME}/war\n" +
                "   --pluginroot             = folder where the plugin archives are expanded into. Default is ${JENKINS_HOME}/plugins\n" +
                "                              (NOTE: this option does not change the directory where the plugin archives are stored)\n" +
                "   --extractedFilesFolder   = folder where extracted files are to be located. Default is the temp folder\n" +
                "   --daemon                 = fork into background and run as daemon (Unix only)\n" +
                "   --logfile                = redirect log messages to this file\n" +
                "{OPTIONS}");

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
            f.set(null,"JSESSIONID."+UUID.randomUUID().toString().replace("-","").substring(0,8));
        } catch (ClassNotFoundException e) {
            // early versions of Winstone 2.x didn't have this
        }

        // run
        mainMethod.invoke(null,new Object[]{arguments.toArray(new String[0])});
    }

    private static void trimOffOurOptions(List arguments) {
        for (Iterator itr = arguments.iterator(); itr.hasNext(); ) {
            String arg = (String) itr.next();
            if (arg.startsWith("--daemon") || arg.startsWith("--logfile") || arg.startsWith("--extractedFilesFolder")
                    || arg.startsWith("--pluginroot"))
                itr.remove();
        }
    }

    private static String getVersion(Map revisions, String groupId, String artifactId) {
        String v = (String)revisions.get(groupId + ":" + artifactId);
        if (v==null) {
            // fall back to artifact ID only search, in case the artifact is renamed
            for (Iterator itr = revisions.keySet().iterator(); itr.hasNext(); ) {
                String key = (String) itr.next();
                if (key.endsWith(":"+artifactId))
                    return v;
            }
        }
        return v;
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
            Field f = ZipFile.class.getDeclaredField("name");
            f.setAccessible(true);
            return new File((String) f.get(jf));
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
