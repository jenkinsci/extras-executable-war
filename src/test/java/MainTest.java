import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For(Main.class)
public class MainTest {

    @Test
    public void shouldFailForOldJava() {
        assertJavaCheckFails(52, false);
        assertJavaCheckFails(52, true);
    }

    @Test
    @Issue("JENKINS-51805")
    public void shouldBeOkForJava11() {
        assertJavaCheckPasses(55, false);
        assertJavaCheckPasses(55, true);
    }

    @Test
    public void shouldFailForMidJavaVersionsIfNoFlag() {
        assertJavaCheckFails(56, false);
        assertJavaCheckPasses(56, true);
        assertJavaCheckFails(57, false);
        assertJavaCheckPasses(57, true);
        assertJavaCheckFails(58, false);
        assertJavaCheckPasses(58, true);
        assertJavaCheckFails(59, false);
        assertJavaCheckPasses(59, true);
        assertJavaCheckFails(60, false);
        assertJavaCheckPasses(60, true);
    }

    @Test
    @Issue("JENKINS-51805")
    public void shouldBeOkForJava17() {
        assertJavaCheckPasses(61, false);
        assertJavaCheckPasses(61, true);
    }

    @Test
    public void shouldFailForNewJavaVersionsIfNoFlag() {
        assertJavaCheckFails(62, false);
        assertJavaCheckPasses(62, true);
        assertJavaCheckFails(63, false);
        assertJavaCheckPasses(63, true);
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
            System.out.printf("Java class version check failed as it was expected for Java class version %s.0 and enableFutureJava=%s%n",
                classVersion, enableFutureJava);
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
        try {
            Main.verifyJavaVersion(classVersion, enableFutureJava);
        } catch (Error error) {
            throw new AssertionError(message != null ? message :
                    String.format("Java version Check should have passed for Java class version %s.0 and enableFutureJava=%s",
                            classVersion, enableFutureJava), error);
        }
    }
}
