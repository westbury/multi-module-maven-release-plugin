package com.github.danielflower.mavenplugins.release;

import org.apache.maven.plugin.logging.Log;

public interface Resolver {

    boolean isResolvable(String groupId, String artifactId, String version, String type, Log log);
}
