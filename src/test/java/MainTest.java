import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Map;

@For(Main.class)
public class MainTest {

    @Test(expected = IOException.class)
    public void shouldHaveNoStandardDependenciesFile() throws IOException {
        final Map<String, String> versions = Main.parseDependencyVersions();
    }

    @Test
    public void shouldFailForOldJava() {
        assertJavaCheckFails(51, false);
        assertJavaCheckFails(51, true);
    }

    @Test
    public void shouldBeOkForJava8() {
        assertJavaCheckPasses(52, false);
        assertJavaCheckPasses(52, true);
    }

    @Test
    public void shouldFailForMidJavaVersionsIfNoFlag() {
        assertJavaCheckFails(53, false);
        assertJavaCheckFails(54, false);
        assertJavaCheckPasses(53, true);
        assertJavaCheckPasses(54, true);
    }

    @Test
    @Issue("JENKINS-51805")
    public void shouldBeOkForJava11() {
        assertJavaCheckPasses(55, false);
        assertJavaCheckPasses(55, true);
    }

    @Test
    public void shouldFailForNewJavaVersionsIfNoFlag() {
        assertJavaCheckFails(56, false);
        assertJavaCheckFails(57, false);
        assertJavaCheckPasses(56, true);
        assertJavaCheckPasses(57, true);
    }

    public void assertJavaCheckFails(int classVersion, boolean enableFutureJava) {
        assertJavaCheckFails(null, classVersion, enableFutureJava);
    }

    public void assertJavaCheckFails(@CheckForNull String message, int classVersion, boolean enableFutureJava) {
        boolean failed = false;
        try {
            Main.verifyJavaVersion(classVersion, enableFutureJava);
        } catch (Error error) {
            failed = true;
            System.out.println(String.format("Java class version check failed as it was expected for Java class version %s.0 and enableFutureJava=%s",
                classVersion, enableFutureJava));
            error.printStackTrace(System.out);
        }

        if (!failed) {
            Assert.fail(message != null ? message :
                    String.format("Java version Check should have failed for Java class version %s.0 and enableFutureJava=%s",
                            classVersion, enableFutureJava));
        }
    }

    public void assertJavaCheckPasses(int classVersion, boolean enableFutureJava) {
        assertJavaCheckPasses(null, classVersion, enableFutureJava);
    }

    public void assertJavaCheckPasses(@CheckForNull String message, int classVersion, boolean enableFutureJava) {
        boolean failed = false;
        try {
            Main.verifyJavaVersion(classVersion, enableFutureJava);
        } catch (Error error) {
            AssertionError err = new AssertionError(message != null ? message :
                    String.format("Java version Check should have passed for Java class version %s.0 and enableFutureJava=%s",
                            classVersion, enableFutureJava));
            err.initCause(error);
            throw err;
        }
    }
}
