package com.winterwell.bob.tasks;

import java.util.Collection;
import java.util.Objects;

public class MavenDependency {

	String groupId; String artifactId; String version;
	/**
	 * see 		// TODO exclude it: https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#dependency-exclusions
	 */
	public Collection<MavenDependency> exclusions;
	
	public MavenDependency(String groupId, String artifactId, String version) {
		this.groupId=groupId;
		this.artifactId=artifactId;
		this.version = version;
	}

	@Override
	public String toString() {
		return "MavenDependency [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version+"]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, exclusions, groupId, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MavenDependency other = (MavenDependency) obj;
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(exclusions, other.exclusions)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(version, other.version);
	}	
	

}
