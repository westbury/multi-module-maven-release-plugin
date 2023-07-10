package com.github.danielflower.mavenplugins.release;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.tycho.versions.engine.EclipseVersionUpdater;
import org.eclipse.tycho.versions.engine.ProjectMetadata;
import org.eclipse.tycho.versions.engine.ProjectMetadataReader;

public class PomUpdater {

    private final Log log;
    private final Reactor reactor;

    private EclipseVersionUpdater metadataUpdater = new EclipseVersionUpdater();

	private ProjectMetadataReader pomReader = new ProjectMetadataReader();


    public PomUpdater(Log log, Reactor reactor) {
        this.log = log;
        this.reactor = reactor;
    }

    public UpdateResult updateVersion() {
        List<File> changedPoms = new ArrayList<File>();
        List<String> errors = new ArrayList<String>();
        for (ReleasableModule module : reactor.getModulesInBuildOrder()) {
        	try {
        		MavenProject project = module.getProject();
                if (module.willBeReleased()) {
                    log.info("Going to release " + module.getArtifactId() + " " + module.getNewVersion());
                }

                List<String> errorsForCurrentPom = alterModel(project, module.getNewVersion());
                errors.addAll(errorsForCurrentPom);

                File pom = project.getFile().getCanonicalFile();
                
                byte[] bytes = Files.readAllBytes(pom.toPath());
                String pomContents = new String(bytes);
                Project pomProjectUpdater = new Project(pomContents);
                
                changedPoms.add(pom);
                
                pomProjectUpdater.setVersion(module.getNewVersion());
                if (module.getProject().getParent() != null) {
                	// Find the module for the parent
                	MavenProject parentProject = module.getProject().getParent();
                	Optional<ReleasableModule> parentModule = findProject(parentProject.getGroupId(), parentProject.getArtifactId());
                	pomProjectUpdater.setParentVersion(parentModule.get().getNewVersion());
                }
                
                for (Dependency dependency : module.getProject().getModel().getDependencies()) {
                	Optional<ReleasableModule> dependentProject = findProject(dependency.getGroupId(), dependency.getArtifactId());
                	// It may be a dependency on a project outside our reactor, in which
                	// case we will never be changing the version.
                	if (dependentProject.isPresent()) {
                		pomProjectUpdater.setDependencyVersion(dependency.getGroupId(), dependency.getArtifactId(), dependentProject.get().getNewVersion());
                	}
                }
                
                String newContents = pomProjectUpdater.getPom();
    			byte[] newBytes = newContents.getBytes();
    			Files.write(pom.toPath(), newBytes);
    			
//???    			releaseDescriptor.setUpdatedFiles(updatedFiles);

            } catch (Exception e) {
                return new UpdateResult(changedPoms, errors, e);
            }
        }
        
        // We now do the Eclipse stuff, which must be done in a separate loop
        // after all pom versions have been updated in the prior loop.
        for (ReleasableModule module : reactor.getModulesInBuildOrder()) {
        	try {
        		MavenProject project = module.getProject();

    			// Do the eclipse stuff
    			Set<String> updatedFiles = null; //??? releaseDescriptor.getUpdatedFiles();
    			if (updatedFiles == null) {
    				updatedFiles = new HashSet<>();
    			}
    			metadataUpdater.setProjects(Collections.singletonList(project));
    			metadataUpdater.setUpdatedFiles(updatedFiles);
    			metadataUpdater.apply();

            } catch (Exception e) {
                return new UpdateResult(changedPoms, errors, e);
            }
        }
        

        return new UpdateResult(changedPoms, errors, null);
    }

	private Optional<ReleasableModule> findProject(String groupId, String artifactId) {
		return reactor.getModulesInBuildOrder().stream().filter(p -> p.getGroupId().equals(groupId) && p.getArtifactId().equals(artifactId)).findFirst();
	}    		

    public static class UpdateResult {
        public final List<File> alteredPoms;
        public final List<String> dependencyErrors;
        public final Exception unexpectedException;

        public UpdateResult(List<File> alteredPoms, List<String> dependencyErrors, Exception unexpectedException) {
            this.alteredPoms = alteredPoms;
            this.dependencyErrors = dependencyErrors;
            this.unexpectedException = unexpectedException;
        }
        public boolean success() {
            return (dependencyErrors.size() == 0) && (unexpectedException == null);
        }
    }

    private List<String> alterModel(MavenProject project, String newVersion) {
        Model originalModel = project.getOriginalModel();
        originalModel.setVersion(newVersion);

        List<String> errors = new ArrayList<String>();

        String searchingFrom = project.getArtifactId();
        MavenProject parent = project.getParent();
        if (parent != null && MavenVersionResolver.isSnapshot(parent.getVersion())) {
            try {
                ReleasableModule parentBeingReleased = reactor.find(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
                originalModel.getParent().setVersion(parentBeingReleased.getVersionToDependOn());
                log.debug(" Parent " + parentBeingReleased.getArtifactId() + " rewritten to version " + parentBeingReleased.getVersionToDependOn());
            } catch (UnresolvedSnapshotDependencyException e) {
                errors.add("The parent of " + searchingFrom + " is " + e.artifactId + " " + e.version);
            }
        }

        Properties projectProperties = project.getProperties();
        for (Dependency dependency : originalModel.getDependencies()) {
            alterSingleDependency(errors, searchingFrom, projectProperties, dependency);
        }

        //Support for dependency management
        if (originalModel.getDependencyManagement() != null) {
            for (Dependency dependency : originalModel.getDependencyManagement().getDependencies()) {
                alterSingleDependency(errors, searchingFrom, projectProperties, dependency);
            }
        }

        //Support for plugin
        if (originalModel.getBuild() != null && originalModel.getBuild().getPlugins() != null) {
            for (Plugin plugin : originalModel.getBuild().getPlugins()) {
                alterSinglePlugin(errors, searchingFrom, projectProperties, plugin);
            }
        }

        //Support for pluginManagement
        if (originalModel.getBuild() != null && originalModel.getBuild().getPluginManagement() != null
        && originalModel.getBuild().getPluginManagement().getPlugins() != null) {
            for (Plugin plugin : originalModel.getBuild().getPluginManagement().getPlugins()) {
                alterSinglePlugin(errors, searchingFrom, projectProperties, plugin);
            }
        }

        for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
            String version = plugin.getVersion();
            if (MavenVersionResolver.isSnapshot(MavenVersionResolver.resolveVersion(version, projectProperties))) {
                if (!isMultiModuleReleasePlugin(plugin) && !isReleasablePlugin(plugin)) {
                    errors.add(searchingFrom + " references plugin " + plugin.getArtifactId() + " " + version);
                }
            }
        }
        return errors;
    }
    
    private boolean isReleasablePlugin(Plugin plugin) {
        try {
            reactor.find(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
            return true;
        } catch (UnresolvedSnapshotDependencyException ignore) {}
        return false;
    }

    private void alterSinglePlugin(List<String> errors, String searchingFrom, Properties projectProperties, Plugin plugin) {
        String version = plugin.getVersion();
        if (!isMultiModuleReleasePlugin(plugin) &&
            MavenVersionResolver.isSnapshot(MavenVersionResolver.resolveVersion(version, projectProperties))) {
            try {
                ReleasableModule pluginBeingReleased = reactor.find(plugin.getGroupId(), plugin.getArtifactId(), version);
                plugin.setVersion(pluginBeingReleased.getVersionToDependOn());
                log.info("Plugin dependency on " + pluginBeingReleased.getArtifactId() + " rewritten to version " + pluginBeingReleased.getVersionToDependOn());
            } catch (UnresolvedSnapshotDependencyException e) {
                errors.add(searchingFrom + " references plugin dependency " + e.artifactId + " " + e.version);
            }
        }
        else {
            log.debug("Plugin dependency on " + plugin.getArtifactId() + " kept at version " + plugin.getVersion());
        }
    }

    private void alterSingleDependency(List<String> errors, String searchingFrom, Properties projectProperties, Dependency dependency) {
        String version = dependency.getVersion();
        if (MavenVersionResolver.isSnapshot(MavenVersionResolver.resolveVersion(version, projectProperties))) {
            try {
                ReleasableModule dependencyBeingReleased = reactor.find(dependency.getGroupId(), dependency.getArtifactId(), version);
                dependency.setVersion(dependencyBeingReleased.getVersionToDependOn());
                log.debug(" Dependency on " + dependencyBeingReleased.getArtifactId() + " rewritten to version " + dependencyBeingReleased.getVersionToDependOn());
            } catch (UnresolvedSnapshotDependencyException e) {
                errors.add(searchingFrom + " references dependency " + e.artifactId + " " + e.version);
            }
        }
        else {
            log.debug(" Dependency on " + dependency.getArtifactId() + " kept at version " + dependency.getVersion());
        }
    }

    private static boolean isMultiModuleReleasePlugin(Plugin plugin) {
        return plugin.getGroupId().equals("com.github.danielflower.mavenplugins") && plugin.getArtifactId().equals("multi-module-maven-release-plugin");
    }


}
