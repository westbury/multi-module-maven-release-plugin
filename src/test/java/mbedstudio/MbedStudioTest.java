package mbedstudio;

import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.MvnRunner;
import scaffolding.Photocopier;
import scaffolding.TestProject;

import java.io.File;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static scaffolding.ExactCountMatcher.noneOf;
import static scaffolding.ExactCountMatcher.oneOf;
import static scaffolding.GitMatchers.hasTag;
import static scaffolding.Photocopier.copyTestProjectToTemporaryLocation;

public class MbedStudioTest {

    static TestProject testProject = null;

    @BeforeClass
    public static void installPluginToLocalRepo() throws MavenInvocationException {
        testProject = project();
    }

    @Test
    public void simpleChangeAndRelease() throws Exception {
        testProject.mvnRelease("1");
        testProject.commitRandomFile(".");
        List<String> output = testProject.mvnRelease("2");
        assertThat(output, noneOf(containsString("No changes have been detected in any modules")));
        assertThat(output, noneOf(containsString("Will use version 1.0.1")));
    }

    private static TestProject project() {
        try {
            File originDir = new File("C:\\Users\\nigwes01\\git\\mbed-studio");
            Git origin = Git.open(originDir);

            File localDir = Photocopier.folderForSampleProject("MbedStudio");
            Git local = Git.cloneRepository()
                .setBare(false)
                .setDirectory(localDir)
                .setURI(originDir.toURI().toString())
                .call();

            return new TestProject(originDir, origin, localDir, local);
        } catch (Exception e) {
            throw new RuntimeException("Error while creating copies of the test project", e);
        }
    }


}
