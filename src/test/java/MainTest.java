import org.junit.Test;
import org.jvnet.hudson.test.For;

import java.io.IOException;
import java.util.Map;

@For(Main.class)
public class MainTest {

    @Test(expected = IOException.class)
    public void shouldHaveNoStandardDependenciesFile() throws IOException {
        final Map<String, String> versions = Main.parseDependencyVersions();
    }
}
