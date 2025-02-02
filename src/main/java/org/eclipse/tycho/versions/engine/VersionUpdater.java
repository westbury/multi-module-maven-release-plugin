/*******************************************************************************
 * Copyright (c) 2011, 2017 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Bachmann electronic GmbH. - #472579 Support setting the version for pomless builds
 *    Bachmann electronic GmbH. - #512326 Support product file names other than artifact id
 *    Guillaume Dufour - Support for release-process like Maven
 *    Bachmann electronic GmbH. - #517664 Support for updating p2iu versions
 *******************************************************************************/
package org.eclipse.tycho.versions.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.model.Feature;
import org.eclipse.tycho.model.IU;
import org.eclipse.tycho.model.ProductConfiguration;
import org.eclipse.tycho.versions.bundle.MutableBundleManifest;
import org.eclipse.tycho.versions.pom.PomFile;
import org.eclipse.tycho.versions.utils.ProductFileFilter;

/**
 * Update pom or Eclipse/OSGi version to make both versions consistent.
 */
public abstract class VersionUpdater {

//    @Requirement
    private Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_INFO, "VersionUpdater");

//    @Requirement
    private final VersionsEngine engine = new VersionsEngine();

    private static interface VersionAdaptor {
        String getVersion(ProjectMetadata project, Logger logger) throws IOException;
    }

    private static final Map<String, VersionAdaptor> updaters = new HashMap<>();

    private List<MavenProject> projects;

    private Set<String> updatedFileNames;

	private Map<MavenProject, ProjectMetadata> pms = new HashMap<>();

	public void setUpdatedFiles(Set<String> updatedFiles) {
		this.updatedFileNames = updatedFiles;
		
	}

    static {
        VersionAdaptor bundleVersionAdaptor = new VersionAdaptor() {
            @Override
            public String getVersion(ProjectMetadata project, Logger logger) throws IOException {
                MutableBundleManifest manifest = MutableBundleManifest
                        .read(new File(project.getBasedir(), "META-INF/MANIFEST.MF"));
                return manifest.getVersion();
            }
        };
        updaters.put(PackagingType.TYPE_ECLIPSE_PLUGIN, bundleVersionAdaptor);
        updaters.put(PackagingType.TYPE_ECLIPSE_TEST_PLUGIN, bundleVersionAdaptor);

        updaters.put(PackagingType.TYPE_ECLIPSE_FEATURE, new VersionAdaptor() {
            @Override
            public String getVersion(ProjectMetadata project, Logger logger) throws IOException {
                Feature feature = Feature.read(new File(project.getBasedir(), Feature.FEATURE_XML));
                return feature.getVersion();
            }
        });

        VersionAdaptor productVersionAdapter = new VersionAdaptor() {
            @Override
            public String getVersion(ProjectMetadata project, Logger logger) throws IOException {
                PomFile pom = project.getMetadata(PomFile.class);
                File productFile = findProductFile(project, pom, logger);
                if (productFile == null) {
                    return null;
                }
                ProductConfiguration product = ProductConfiguration.read(productFile);
                return product.getVersion();
            }
        };
        updaters.put(PackagingType.TYPE_ECLIPSE_APPLICATION, productVersionAdapter);
        updaters.put(PackagingType.TYPE_ECLIPSE_REPOSITORY, productVersionAdapter);
        updaters.put(PackagingType.TYPE_P2_IU, new VersionAdaptor() {

            @Override
            public String getVersion(ProjectMetadata project, Logger logger) throws IOException {
                IU iu = IU.loadIU(project.getBasedir());
                return iu.getVersion();
            }
        });
    }

    public void setProjects(List<MavenProject> projects) {
        this.projects = projects;
        
        Collection<ProjectMetadata> ps = new ArrayList<ProjectMetadata>();
        for (MavenProject project : projects) {
            ProjectMetadata pm = pms.get(project);
            if (pm == null) {
            	pm = new ProjectMetadata(project.getBasedir());
            	pms.put(project, pm);

				try {
					PomFile pom = PomFile.read(project.getFile(), false);
	                pm.putMetadata(pom);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
}
            ps.add(pm);
        }
        
        engine.setProjects(ps);
    }

    public void apply() throws IOException {
        for (MavenProject project : projects) {

            String pomVersion = project.getOriginalModel().getVersion(); // this is the new version

            String packaging = project.getPackaging();
            VersionAdaptor adaptor = updaters.get(packaging);

            ProjectMetadata pm = pms.get(project);
            if (pm == null) {
            	pm = new ProjectMetadata(project.getBasedir());
            	pms.put(project, pm);

            	try {
					PomFile pom = PomFile.read(project.getFile(), false);
	                pm.putMetadata(pom);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            if (adaptor != null) {
                String osgiVersion = Versions.toCanonicalVersion(adaptor.getVersion(pm, logger));

                if (osgiVersion != null && !Versions.isVersionEquals(pomVersion, osgiVersion)) {
                	PomFile pom = PomFile.read(project.getFile(), false);
                    addVersionChange(engine, pom, osgiVersion);
                }
            }
        }

        engine.apply();
        
        for (File file : engine.updatedFiles) {
        	updatedFileNames.add(file.getCanonicalPath());
        }
    }

    protected abstract void addVersionChange(VersionsEngine engine, PomFile pom, String osgiVersion);

    private static File findProductFile(ProjectMetadata project, PomFile pom, Logger logger) {
        File productFile = new File(project.getBasedir(), pom.getArtifactId() + ".product");
        if (productFile.exists()) {
            return productFile;
        }
        File[] productFiles = project.getBasedir().listFiles(new ProductFileFilter());
        if (productFiles == null || productFiles.length == 0) {
            logger.warn("Skipping updating pom in directory " + project.getBasedir()
                    + " because no product file found to extract the (new) version");
            return null;
        }
        if (productFiles.length > 1) {
            logger.warn("Skipping updating pom in directory " + project.getBasedir()
                    + " because more than one product files have been found. Only one product file is supported or one must be named <artifactId>.product");
            return null;
        }
        return productFiles[0];
    }
}
