package scaffolding;

import static com.github.danielflower.mavenplugins.release.FileUtils.pathOf;
import static scaffolding.Photocopier.copyTestProjectToTemporaryLocation;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.github.danielflower.mavenplugins.release.BaseMojo;
import com.github.danielflower.mavenplugins.release.NoChangesAction;
import com.github.danielflower.mavenplugins.release.ReleaseMojo;

public class TestProject {

    private static final MvnRunner defaultRunner = new MvnRunner(null);
    private static final String PLUGIN_VERSION_FOR_TESTS = "3.6-SNAPSHOT";

    public final File originDir;
    public final Git origin;

    public final File localDir;
    public final Git local;

    private final AtomicInteger commitCounter = new AtomicInteger(1);
    private MvnRunner mvnRunner = defaultRunner;

	private Set<String> removedFromRepository = new HashSet<>();
	
	public boolean runInProcess = true;

    public TestProject(File originDir, Git origin, File localDir, Git local) {
        this.originDir = originDir;
        this.origin = origin;
        this.localDir = localDir;
        this.local = local;
    }

    /**
     * Runs a mvn command against the local repo and returns the console output.
     */
    public List<String> mvn(String... arguments) throws IOException {
        return mvnRunner.runMaven(localDir, arguments);
    }

    public void setMvnOpts(String mavenOpts) {
        mvnRunner.mavenOpts = mavenOpts;
    }

    public List<String> mvnRelease(String buildNumber) throws IOException, InterruptedException {
        return mvnRunner.runMaven(localDir,
            "-DbuildNumber=" + buildNumber,
            "releaser:release");
    }

    public List<String> mvnRelease(String buildNumberAsString, String...arguments) throws IOException, InterruptedException {
//        return mvnRun("releaser:release", buildNumber, arguments);

    	List<String> output = new ArrayList<>();

		String previousPath = System.setProperty("user.dir", localDir.getAbsolutePath());
    	
    	try {

    	ReleaseMojo mojo = new ReleaseMojo() {
    		{
    			projects = new ArrayList<MavenProject>();
    			project = readProject(localDir.toPath().toAbsolutePath());
    	
    			sort(projects);
    			
    			this.buildNumber = Long.valueOf(buildNumberAsString);
    			this.noChangesAction = NoChangesAction.ReleaseAll;
    			this.resolverWrapper = new com.github.danielflower.mavenplugins.release.Resolver() {

					@Override
    				public boolean isResolvable(String groupId, String artifactId, String version, String type, Log log) {
    					return !removedFromRepository.contains(groupId + ":" + artifactId);
    				}    		
    			};
    			
    			for (String argument : arguments) {
    				switch (argument) {
    				case "-DnoChangesAction=FailBuild":
    					this.noChangesAction = NoChangesAction.FailBuild;
    					break;
    				case "-DnoChangesAction=ReleaseAll":
    					this.noChangesAction = NoChangesAction.ReleaseAll;
    					break;
    				case "-DnoChangesAction=ReleaseNone":
    					this.noChangesAction = NoChangesAction.ReleaseNone; 					
    					break;
    				}
    			}
    			
    			// Iterate over a copy of the list of projects, because parent projects
    			// may be added to the list.
    			List<MavenProject> preexistingProjects = new ArrayList<>(projects);
    	    	for (MavenProject childProject : preexistingProjects) {
    				setParentReference(childProject);
    	    	}
    		}

			private void setParentReference(MavenProject childProject) {
				if (childProject.getParent() == null) {
					Parent parentCoordinates = childProject.getModel().getParent();
					if (parentCoordinates != null) {
						Optional<MavenProject> parentProject = findProject(projects, parentCoordinates.getGroupId(), parentCoordinates.getArtifactId());
						
						MavenProject parentProjectValue;
						if (!parentProject.isPresent()) {
							String relativePathToParent = parentCoordinates.getRelativePath();
							if (relativePathToParent == null) {
								throw new RuntimeException("Parent project not in projects and no relative path specified");
							}
							
							Path pathToParent = childProject.getBasedir().toPath().resolve(relativePathToParent);
							parentProjectValue = readProject(pathToParent);
							projects.add(parentProjectValue);
							
							// And set the parent reference for the parent project if needed (recursive call)
							setParentReference(parentProjectValue);
						} else {
							parentProjectValue = parentProject.get();
						}
						childProject.setParent(parentProjectValue);
						childProject.getOriginalModel().setParent(parentCoordinates);
					} else {
						childProject.setParent(null);
					}
				}
			}

    		public Log getLog() {
    			return new SystemStreamLog() {
    			    public void info(CharSequence content) {
    			        output.add("[INFO] " + content.toString());
    			        super.info(content);
    			    }
    			    public void warn(CharSequence content) {
    			        output.add("[WARN] " + content.toString());
    			        super.warn(content);
    			    }
    			};
    		}
			private MavenProject readProject(Path projectPath) {
				Model model = null;
    			MavenXpp3Reader mavenreader = new MavenXpp3Reader();
				File pomfile = projectPath.resolve("pom.xml").toFile();
    			try (FileReader reader = new FileReader(pomfile)) {
    			    model = mavenreader.read(reader);
    			} catch (IOException | XmlPullParserException e) {
    				throw new RuntimeException(e);
    			}
    			MavenProject project = new MavenProject(model);
    			
    			project.setBasedir(projectPath.toFile());
    			project.setFile(pomfile);
    			
    			Model originalModel = new Model();
				project.setOriginalModel(originalModel);
    			
//    			Scm scm = new Scm();
//    			originalModel.setScm(scm);
//    			project.getModel().setScm(scm);
    			
				if (originalModel.getBuild() == null) {
					Build originalBuild = new Build();
					originalModel.setBuild(originalBuild);
				}
				if (model.getBuild() == null) {
					Build build = new Build();
					model.setBuild(build);
				}
				
				projects.add(project);
    			for (String module : model.getModules()) {
    				Path modulePath = projectPath.resolve(module);
    				MavenProject childProject = readProject(modulePath);
    				
    				// Can't do this now because parent may not be created.
//    				if (childProject.getParent() == null) {
//        				if (childProject.getModel().getParent() != null) {
//        					childToParentArtifactIdMap.put(childProject)
//        				} else {
//        					childProject.setParent(project);
//        				}
//    				}
    				
    				Parent parentCoordinates = childProject.getModel().getParent();
    				childProject.getOriginalModel().setParent(parentCoordinates);
    			}
    			
    			return project;
			}
    	};

		try {
			// Not sure where Install comes from.  It's in the pom, and can be read from the model
			List<String> goals = Collections.singletonList("install");
			Class<?> clazz = ReleaseMojo.class;
			Field f1 = clazz.getDeclaredField("goals");
			f1.setAccessible(true);
			f1.set(mojo, goals);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		try {
			// pushTags defaults to true
			Field f1 = ReleaseMojo.class.getDeclaredField("pushTags");
			f1.setAccessible(true);
			f1.set(mojo, true);

			// pullTags defaults to true
			Field f2 = BaseMojo.class.getDeclaredField("pullTags");
			f2.setAccessible(true);
			f2.set(mojo, true);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

			mojo.execute();
		} catch (MojoExecutionException | MojoFailureException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		} finally {
			System.setProperty("user.dir", previousPath);
		}
    	
    	return output;
    }

	protected void sort(List<MavenProject> projects) {
		int i = 0;
		while (i < projects.size()) {
			MavenProject project1 = projects.get(i);
		
			boolean dependencyMoved = false;
			for (Dependency dependency : project1.getModel().getDependencies()) {
				// We could start search later than i+1 if we have already moved a dependency,
				// but this will work.
				for (int j = i+1; j < projects.size(); j++) {
					MavenProject project2 = projects.get(j);

					if (dependency.getGroupId().equals(project2.getGroupId()) && dependency.getArtifactId().equals(project2.getArtifactId())) {
						projects.remove(j);
						projects.add(i, project2);
						dependencyMoved = true;
						break;
					}
				}
            }
				
			if (!dependencyMoved) {
				/*
				 * There are no dependencies later than this project, so we are fine up to
				 * this project and can move on to the next project.
				 */
				i++;
			}
        }
	}

	private static Optional<MavenProject> findProject(List<MavenProject> projects, String groupId, String artifactId) {
		Optional<MavenProject> parentProject = projects.stream().filter(p -> p.getGroupId().equals(groupId) && p.getArtifactId().equals(artifactId)).findFirst();
		return parentProject;
	}    		

    public List<String> mvnReleaserNext(String buildNumber, String...arguments) throws IOException, InterruptedException {
        return mvnRun("releaser:next", buildNumber, arguments);
    }

    public TestProject commitRandomFile(String module) throws IOException, GitAPIException {
        File moduleDir = new File(localDir, module);
        if (!moduleDir.isDirectory()) {
            throw new RuntimeException("Could not find " + moduleDir.getCanonicalPath());
        }
        File random = new File(moduleDir, UUID.randomUUID() + ".txt");
        random.createNewFile();
        String modulePath = module.equals(".") ? "" : module + "/";
        local.add().addFilepattern(modulePath + random.getName()).call();
        local.commit().setMessage("Commit " + commitCounter.getAndIncrement() + ": adding " + random.getName()).call();
        return this;
    }

    public TestProject checkoutBranch(String branch) throws GitAPIException {
        local.checkout().setName(branch).call();
        return this;
    }

    public TestProject createBranch(String branch) throws GitAPIException {
        local.branchCreate().setName(branch).call();
        return this;
    }

    public void pushIt() throws GitAPIException {
        local.push().call();
    }

    private List<String> mvnRun(String goal, String buildNumber, String[] arguments) {
        String[] args = new String[arguments.length + 2];
        args[0] = "-DbuildNumber=" + buildNumber;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        args[args.length-1] = goal;
        return mvnRunner.runMaven(localDir, args);
    }

    private static TestProject project(String name) {
        try {
            File originDir = copyTestProjectToTemporaryLocation(name);
            performPomSubstitution(originDir);

            InitCommand initCommand = Git.init();
            initCommand.setDirectory(originDir);
            Git origin = initCommand.call();

            origin.add().addFilepattern(".").call();
            origin.commit().setMessage("Initial commit").call();

            File localDir = Photocopier.folderForSampleProject(name);
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

    public static void performPomSubstitution(File sourceDir) throws IOException {
        File pom = new File(sourceDir, "pom.xml");
        if (pom.exists()) {
            String xml = FileUtils.readFileToString(pom, "UTF-8");
            if (xml.contains("${scm.url}")) {
                xml = xml.replace("${scm.url}", dirToGitScmReference(sourceDir));
            }
            xml = xml.replace("${current.plugin.version}", PLUGIN_VERSION_FOR_TESTS);
            FileUtils.writeStringToFile(pom, xml, "UTF-8");
        }
        for (File child : sourceDir.listFiles((FileFilter) FileFilterUtils.directoryFileFilter())) {
            performPomSubstitution(child);
        }
    }

    public static TestProject localPluginProject() {
        return project("local-plugin");
    }

    public static String dirToGitScmReference(File sourceDir) {
        return "scm:git:file://localhost/" + pathOf(sourceDir).replace('\\', '/').toLowerCase();
    }

    public static TestProject singleModuleProject() {
        return project("single-module");
    }

    public static TestProject nestedProject() {
        return project("nested-project");
    }

    public static TestProject moduleWithScmTag() {
        return project("module-with-scm-tag");
    }

    public static TestProject moduleWithProfilesProject() {
        return project("module-with-profiles");
    }

    public static TestProject inheritedVersionsFromParent() {
        return project("inherited-versions-from-parent");
    }

    public static TestProject independentVersionsProject() {
        return project("independent-versions");
    }

    public static TestProject versionReportProject() {
        return project("version-report");
    }

    public static TestProject parentAsSibilngProject() {
        return project("parent-as-sibling");
    }

    public static TestProject deepDependenciesProject() {
        return project("deep-dependencies");
    }

    public static TestProject dependencyManagementProject() {
        return project("dependencymanagement");
    }

    public static TestProject dependencyManagementUsingParentModuleVersionPropertyProject() {
	    return project("dependencymanagement-using-parent-module-version-property");
    }

    public static TestProject moduleWithTestFailure() {
        return project("module-with-test-failure");
    }

    public static TestProject moduleWithSnapshotDependencies() {
        return project("snapshot-dependencies");
    }
    public static TestProject moduleWithSnapshotDependenciesWithVersionProperties() {
        return project("snapshot-dependencies-with-version-properties");
    }

    public static TestProject differentDelimiterProject() {
        return project("different-delimiter");
    }

    public void setMvnRunner(MvnRunner mvnRunner) {
        this.mvnRunner = mvnRunner;
    }

	public void purgeFromRepository(String groupId, String artifactId) throws IOException {
		if (runInProcess) {
			removedFromRepository.add(groupId + ":" + artifactId);
		} else {
	        mvn(MessageFormat.format("dependency:purge-local-repository -DactTransitively=false -DreResolve=false " +
          "-DmanualInclude={0}:{1}", groupId, artifactId));
		}
	}
}
