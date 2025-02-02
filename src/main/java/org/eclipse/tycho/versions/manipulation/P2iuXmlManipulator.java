/*******************************************************************************
 * Copyright (c) 2008, 2016 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Bachmann electronic GmbH. - #472579 - Support setting the version for pomless builds
 *******************************************************************************/
package org.eclipse.tycho.versions.manipulation;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.model.IU;
import org.eclipse.tycho.versions.engine.MetadataManipulator;
import org.eclipse.tycho.versions.engine.ProjectMetadata;
import org.eclipse.tycho.versions.engine.VersionChange;
import org.eclipse.tycho.versions.engine.VersionChangesDescriptor;
import org.eclipse.tycho.versions.pom.PomFile;

@Component(role = MetadataManipulator.class, hint = "p2-installable-unit")
public class P2iuXmlManipulator extends AbstractMetadataManipulator {

    @Override
    public void applyChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext) {
        if (!isIu(project)) {
            return;
        }
        IU iu = getIU(project);
        VersionChange change = versionChangeContext.findVersionChangeByArtifactId(iu.getId());
        if (change != null && change.getVersion().equals(iu.getVersion())) {
            logger.info("  p2iu.xml//unit/@version: " + change.getVersion() + " => " + change.getNewVersion());
            iu.setVersion(change.getNewVersion());
            // version changed, so put the iu into the metadata so that the writeMetadata method can pick it up
            project.putMetadata(iu);
        }
    }

    @Override
    public void writeMetadata(ProjectMetadata project, Set<File> updatedFiles) throws IOException {
        if (!isIu(project)) {
            return;
        }
        IU iu = project.getMetadata(IU.class);
        if (iu != null) {
            File iuFile = new File(project.getBasedir(), IU.SOURCE_FILE_NAME);
			IU.write(iu, iuFile, null);
			updatedFiles.add(iuFile);
        }
    }

    @Override
    public Collection<String> validateChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext) {
        return null;
    }

    private IU getIU(ProjectMetadata project) {
        IU iu = project.getMetadata(IU.class);
        if (iu == null) {
            iu = IU.loadIU(project.getBasedir());
        }
        return iu;
    }

    private boolean isIu(ProjectMetadata project) {
        PomFile pom = project.getMetadata(PomFile.class);
        return PackagingType.TYPE_P2_IU.equals(pom.getPackaging());
    }

}
