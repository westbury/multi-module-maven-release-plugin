/*******************************************************************************
 * Copyright (c) 2008, 2011 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.versions.engine;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public interface MetadataManipulator {

    public boolean addMoreChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext);

    public Collection<String> validateChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext);

    public void applyChanges(ProjectMetadata project, VersionChangesDescriptor versionChangeContext);

    public void writeMetadata(ProjectMetadata project, Set<File> updatedFiles) throws IOException;
}
