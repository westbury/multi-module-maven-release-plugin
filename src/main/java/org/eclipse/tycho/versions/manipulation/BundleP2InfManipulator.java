/*******************************************************************************
 * Copyright (c) 2008, 2015 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Sebastien Arod - update version ranges
 *******************************************************************************/
package org.eclipse.tycho.versions.manipulation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.model.BundleP2Inf;
import org.eclipse.tycho.versions.engine.MetadataManipulator;
import org.eclipse.tycho.versions.engine.PomVersionChange;
import org.eclipse.tycho.versions.engine.ProjectMetadata;
import org.eclipse.tycho.versions.engine.VersionChangesDescriptor;

@Component(role = MetadataManipulator.class, hint = "bundle-manifest")
public class BundleP2InfManipulator extends AbstractMetadataManipulator {

    @Override
    public Collection<String> validateChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext) {
        return null;
    }

    @Override
    public void applyChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext) {
        if (isBundle(project)) {
            updateTouchpointVersionRanges(project, versionChangeContext);
        }
    }

    @Override
    public void writeMetadata(ProjectMetadata project, Set<File> updatedFiles) throws IOException {
    	BundleP2Inf p2InfFile = project.getMetadata(BundleP2Inf.class);
        if (p2InfFile != null) {
            Path file = getP2InfFile(project);
            BundleP2Inf.write(p2InfFile, file);
            updatedFiles.add(file.toFile());
        }
    }

    private void updateTouchpointVersionRanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext) {
    	BundleP2Inf p2InfFile = getBundleP2Inf(project);
    	if (p2InfFile != null) {
    		Map<String, String> requiredBundleVersions = p2InfFile.getRequiredBundleVersions();
    		Map<String, String> versionsToUpdate = new HashMap<>();
    		for (PomVersionChange versionChange : versionChangeContext.getVersionChanges()) {
    			String bundleSymbolicName = versionChange.getArtifactId();
    			if (requiredBundleVersions.containsKey(bundleSymbolicName)) {
    				String originalVersionRange = requiredBundleVersions.get(bundleSymbolicName);
    				versionsToUpdate.put(bundleSymbolicName,
    						versionChangeContext.getVersionRangeUpdateStrategy().computeNewVersionRange(
    								originalVersionRange, versionChange.getVersion(), versionChange.getNewVersion()));
    			}
    		}
    		p2InfFile.updateRequiredBundleVersions(versionsToUpdate);
    	}
    }

    /**
     * 
     * @param project
     * @return an object containing p2.inf content, or null if there was no p2.inf
     */
    private BundleP2Inf getBundleP2Inf(ProjectMetadata project) {
    	BundleP2Inf p2InfFile = project.getMetadata(BundleP2Inf.class);
    	if (p2InfFile == null) {
    		Path file = getP2InfFile(project);
    		if (Files.exists(file)) {
    			try {
    				p2InfFile = BundleP2Inf.read(file);
    			} catch (IOException e) {
    				throw new IllegalArgumentException("Could not read bundle p2.inf " + file, e);
    			}
    			project.putMetadata(p2InfFile);
    		}
    	}
    	return p2InfFile;
    }

    private Path getP2InfFile(ProjectMetadata project) {
        return project.getBasedir().toPath().resolve("META-INF/p2.inf");
    }

}
