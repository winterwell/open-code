
# Migrating from Bob to Maven

Status:
Dan: I have confirmed that you can build a project tree with Maven.
And you can also build with a mix of Maven and Bob.
The goal is to migrate all to maven.

To migrate an Eclipse project, e.g. `winterwell.foo`

1. Have strong drink to hand, as build systems are painful (all of them).
2. Build bob locally. You want 1.7.0+, which adds support for mixed maven/bob. There's some risk of breakage, so I haven't released that yet. But if you build locally from this branch, then you'll get it.
	`cd winterwell.bob`
	`bob`
	`bob --version`
Since 1.7 isn't released, you'll probably have to copy the `bob-all.jar` file into place. `bob --version` will tell you the command line (and hence the jar file) that it's using.
3. mv winterwell.foo winterwell.foo.old
4. In Eclipse: 
 - delete winterwell.foo from the workspace (but not the files on disk) 
 - make a new Maven project with group-id good-loop.com, artifact-id: winterwell.foo (this will set the maven nature in the Eclipse.project file)
 - Close Eclipse
5. Copy the src and test folders from winterwell.foo.old into the src/main/java and src/test/java folders in winterwell.foo
6. meld winterwell.foo/pom.xml with winterwell.web/pom.xml to get some GL-standard maven setup (like Java command line settings)
7. meld winterwell.foo/pom.xml with winterwell.foo.old/pom.xml to get the project's dependencies, as set in Bob's build script BuildFoo.java
8. Unfortunately this doesn't cover transitive dependencies from other projects higher up the dependency chain. 
	- E.g. winterwell.datalog needs HttpServlet. In Eclipse/Bob it gets that via winterwell.web. With Maven, you have to add the dependency (which in this example, is org.eclipse.jetty:jetty-servlet:10.0.7).
	- You'll see missing dependency error messages in Eclipse.
	- Copy maven dependencies from the higher-up pom.xml files into the new pom.xml
	- Bob projects can have maven dependencies added in their BuildX.java file.
		- Run bob to fetch the extra dependencies down into a local folder, and then add them to the Eclipse build-path.
9. Try building. Swear. Debug. Try again. Repent of your sins for wishing to create software when only G-d creates. Google Maven issues. ...Success.
