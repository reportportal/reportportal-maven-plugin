package com.epam.reportportal;

import com.epam.reportportal.utils.properties.PropertiesLoader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ReportPortalInstallMojo extends AbstractMojo {

	private static final String RP_DEPENDENCY_GROUP = "com.epam.reportportal";
	private static final String RP_PLUGIN_ID = RP_DEPENDENCY_GROUP + ":reportportal-maven-plugin";
	private static final String TEST_NG_ID = "org.testng:testng";
	private static final String JUNIT_5_ID = "org.junit.jupiter:junit-jupiter-api";

	private static final Predicate<Artifact> RP_DEPENDENCY = a -> a.getDependencyTrail().size() > 1
			&& a.getDependencyTrail().get(1).startsWith(RP_DEPENDENCY_GROUP);
	private static final Predicate<Artifact> TEST_NG_DEPENDENCY = a -> a.getDependencyTrail()
			.stream()
			.anyMatch(d -> d.startsWith(TEST_NG_ID));
	private static final Predicate<Artifact> JUNIT_5_DEPENDENCY = a -> a.getDependencyTrail()
			.stream()
			.anyMatch(d -> d.startsWith(JUNIT_5_ID));
	private static final Predicate<Artifact> JUNIT_5_OR_TEST_NG_DEPENDENCY = TEST_NG_DEPENDENCY.or(JUNIT_5_DEPENDENCY);
	private static final Predicate<Artifact> NOT_JUNIT_5_OR_TEST_NG_DEPENDENCY = JUNIT_5_OR_TEST_NG_DEPENDENCY.negate();

	private static final String JUNIT5_EXTENSION_FILE_NAME = "org.junit.jupiter.api.extension.Extension";
	private static final String JUNIT5_EXTENSION_FILE_DESTINATION_PATH = "META-INF/services";
	private static final String JUNIT5_PROPERTY_FILE_NAME = "junit-platform.properties";
	private static final String JUNIT5_EXTENSION_FILE_SOURCE_PATH = "services/" + JUNIT5_EXTENSION_FILE_NAME;

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(defaultValue = "${plugin}", readonly = true, required = true)
	private PluginDescriptor pluginDescriptor;

	@Parameter(property = "project.build.directory", defaultValue = "${project.build.directory}", readonly = true)
	private String buildDirectoryPath;

	@Parameter(defaultValue = "${project.build.testOutputDirectory}")
	private File testClassesDirectory;

	private void overrideProperties() throws MojoExecutionException {
		Properties pluginProperties = new Properties();
		pluginProperties.putAll(project.getBuildPlugins()
				.stream()
				.filter(p -> p.getKey().equals(RP_PLUGIN_ID))
				.map(p -> ofNullable(p.getConfiguration()).map(c -> (Xpp3Dom) c)
						.orElseGet(() -> new Xpp3Dom("configuration")))
				.flatMap(c -> Arrays.stream(c.getChildren()))
				.filter(kv -> kv.getName().startsWith("rp."))
				.collect(Collectors.toMap(Xpp3Dom::getName, Xpp3Dom::getValue)));

		String propertyFilePathStr = pluginProperties.containsKey(PropertiesLoader.PROPERTIES_PATH_PROPERTY) ?
				pluginProperties.getProperty(PropertiesLoader.PROPERTIES_PATH_PROPERTY) :
				PropertiesLoader.getPropertyFilePath();
		Path propertyFilePath = Paths.get(testClassesDirectory.getPath(), propertyFilePathStr);

		Properties fsProperties = new Properties();
		if (propertyFilePath.toFile().exists()) {
			try {
				fsProperties.load(Files.newInputStream(propertyFilePath));
			} catch (FileNotFoundException e) {
				// Override property file then
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to read Report Portal property file", e);
			}
		}
		fsProperties.putAll(pluginProperties);
		try {
			FileUtils.forceMkdir(propertyFilePath.getParent().toFile());
			fsProperties.store(Files.newOutputStream(propertyFilePath), "Report Portal generated properties");
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to write Report Portal property file", e);
		}
	}

	private static byte[] getFileContent(String path) throws MojoExecutionException {
		byte[] extensionFileContent;
		try {
			extensionFileContent = IOUtils.toByteArray(ofNullable(Thread.currentThread()
					.getContextClassLoader()
					.getResourceAsStream(path)).orElseThrow(() -> new MojoExecutionException(
					"Unable to find JUnit 5 file: " + path)));
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to read JUnit 5 file: " + path, e);
		}
		return extensionFileContent;
	}

	private void setupJunit5() throws MojoExecutionException {
		byte[] extensionFileContent = getFileContent(JUNIT5_EXTENSION_FILE_SOURCE_PATH);
		byte[] propertyFileContent = getFileContent(JUNIT5_PROPERTY_FILE_NAME);
		Path extensionFileDestinationPath = Paths.get(testClassesDirectory.getPath(),
				JUNIT5_EXTENSION_FILE_DESTINATION_PATH,
				JUNIT5_EXTENSION_FILE_NAME
		);
		Path propertyFileDestinationPath = Paths.get(testClassesDirectory.getPath(), JUNIT5_PROPERTY_FILE_NAME);
		try {
			if (extensionFileDestinationPath.toFile().exists()) {
				Files.write(extensionFileDestinationPath, new byte[] { '\n' }, StandardOpenOption.APPEND);
				Files.write(extensionFileDestinationPath, extensionFileContent, StandardOpenOption.APPEND);
			} else {
				extensionFileDestinationPath.toFile().getParentFile().mkdirs();
				Files.write(extensionFileDestinationPath, extensionFileContent, StandardOpenOption.CREATE_NEW);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to write JUnit 5 extension file", e);
		}
		try {
			if (!propertyFileDestinationPath.toFile().exists()) {
				Files.write(propertyFileDestinationPath, propertyFileContent);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to write JUnit 5 property file", e);
		}
	}

	private void setupTestNg() {
		// TODO: implement
	}

	private void setupLogbackLogger() {
		// TODO: implement
	}

	private void setupLog4jLogger() {
		// TODO: implement
	}

	public void addDependencies() throws MojoExecutionException {
		List<Artifact> pluginArtifacts = pluginDescriptor.getArtifacts();
		List<Artifact> rpArtifact = pluginArtifacts.stream()
				.filter(d -> !d.getArtifactId().equals(pluginDescriptor.getArtifactId()))
				.filter(RP_DEPENDENCY)
				.filter(NOT_JUNIT_5_OR_TEST_NG_DEPENDENCY)
				.collect(Collectors.toList());
		getLog().debug("Report Portal Plugin artifacts: " + rpArtifact);
		for (Artifact a : rpArtifact) {
			getLog().debug("Adding artifact: " + a.getId());
			File sourceFile = a.getFile();
			try (JarFile jar = new JarFile(sourceFile)) {
				Enumeration<JarEntry> jarEntries = jar.entries();
				while (jarEntries.hasMoreElements()) {
					JarEntry entry = jarEntries.nextElement();
					File destFile = Paths.get(testClassesDirectory.getPath(), entry.getName()).toFile();
					if (destFile.exists()) {
						continue;
					}
					if (entry.isDirectory()) {
						FileUtils.forceMkdir(destFile);
						continue;
					}
					try (InputStream input = jar.getInputStream(entry);
							FileOutputStream output = new FileOutputStream(destFile)) {
						IOUtils.copy(input, output);
					}
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to open jar file: " + sourceFile.getAbsolutePath());
			}
		}
	}

	@Override
	public void execute() throws MojoExecutionException {
		overrideProperties();
		setupJunit5();
		setupTestNg();
		setupLogbackLogger();
		setupLog4jLogger();
		addDependencies();
	}
}
