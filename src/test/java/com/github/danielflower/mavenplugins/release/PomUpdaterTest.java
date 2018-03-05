package com.github.danielflower.mavenplugins.release;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static scaffolding.ReleasableModuleBuilder.aModule;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.junit.Assert;
import org.junit.Test;

import com.github.danielflower.mavenplugins.release.PomUpdater.UpdateResult;

public class PomUpdaterTest {


	private final Log log = mock(Log.class);

	@Test
	public void canFindModulesByGroupAndArtifactName() throws Exception {
		ReleasableModule arty = aModule().withGroupId("my.great.group").withArtifactId("some-arty").build();
		Reactor reactor = new Reactor(asList(
				aModule().build(), arty, aModule().build()
				));

		for (ReleasableModule module : reactor.getModulesInBuildOrder()) {
			Model originalModel = new Model();
			module.getProject().setOriginalModel(originalModel);

			Model model = module.getProject().getModel();
			model.setBuild(new Build());

			File file = File.createTempFile("pom", "xml");

			String[] contents = new String[] {
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
							"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\r\n" + 
							"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + 
							"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" + 
							"    <modelVersion>4.0.0</modelVersion>\r\n" + 
							"\r\n" + 
							"    <groupId>com.github.danielflower.mavenplugins</groupId>\r\n" + 
							"    <artifactId>multi-module-maven-release-plugin</artifactId>\r\n" + 
							"    <version>2.2-SNAPSHOT</version> <!-- When changing also update scaffolding.TestProject.PLUGIN_VERSION_FOR_TESTS and add to src/site/markdown/changelog.md -->\r\n" + 
							"</project>\r\n" + 
			""};
			List<String> contentsAsList = new ArrayList<>();
			for (String content : contents) {
				contentsAsList.add(content);
			}
			Files.write(file.toPath(), contentsAsList);
			module.getProject().setFile(file);
		}

		PomUpdater updater = new PomUpdater(log, reactor);

		UpdateResult result = updater.updateVersion();

		if (result.unexpectedException != null) {
			throw result.unexpectedException;
		}

		assertThat(reactor.find("my.great.group", "some-arty", "1.0-SNAPSHOT"), is(arty));
		assertThat(reactor.findByLabel("my.great.group:some-arty"), is(arty));
	}

	@Test
	public void testLocation() throws XMLStreamException {
		String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + 
				"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\r\n" + 
				"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + 
				"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" + 
				"    <modelVersion>4.0.0</modelVersion>\r\n" + 
				"\r\n" + 
				"    <groupId>com.github.danielflower.mavenplugins</groupId>\r\n" + 
				"    <artifactId>multi-module-maven-release-plugin</artifactId>\r\n" + 
				"    <version>2.2-SNAPSHOT</version> <!-- When changing also update scaffolding.TestProject.PLUGIN_VERSION_FOR_TESTS and add to src/site/markdown/changelog.md -->\r\n" + 
				"    <dependencies>\r\n" + 
				"        <dependency>\r\n" + 
				"            <groupId>com.jcraft</groupId>\r\n" + 
				"            <artifactId>jsch.agentproxy.sshagent</artifactId>\r\n" + 
				"            <version>${jsch.agentproxy.version}</version>\r\n" + 
				"        </dependency>\r\n" + 
				"        <dependency>\r\n" + 
				"            <groupId>com.jcraft</groupId>\r\n" + 
				"            <artifactId>jsch.agentproxy.core</artifactId>\r\n" + 
				"            <version>${jsch.agentproxy.version}</version>\r\n" + 
				"        </dependency>\r\n" + 
				"    </dependencies>\r\n" + 
				"</project>\r\n";

			Project project = new Project(XML);

			Assert.assertTrue(project.version.text.equals("2.2-SNAPSHOT"));
			
			project.setVersion("2.2.123");
			
			String newPom = project.getPom();
			
			Assert.assertTrue(newPom.contains("<version>2.2.123</version>"));
	}



}



