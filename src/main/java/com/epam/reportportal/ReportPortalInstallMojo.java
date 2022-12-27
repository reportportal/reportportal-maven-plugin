package com.epam.reportportal;

import com.epam.reportportal.utils.properties.PropertiesLoader;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ReportPortalInstallMojo extends AbstractMojo {

	private static final String RP_PLUGIN_ID = "com.epam.reportportal:reportportal-maven-plugin";

	private static final String JUNIT5_EXTENSION_FILE_NAME = "org.junit.jupiter.api.extension.Extension";
	private static final String JUNIT5_EXTENSION_FILE_DESTINATION_PATH = "META-INF/services";
	private static final String JUNIT5_PROPERTY_FILE_NAME = "junit-platform.properties";
	private static final String JUNIT5_EXTENSION_FILE_SOURCE_PATH = "services/" + JUNIT5_EXTENSION_FILE_NAME;

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(property = "project.build.directory", defaultValue = "${project.build.directory}", readonly = true)
	private String buildDirectoryPath;

	@Parameter(defaultValue = "${project.build.testOutputDirectory}")
	private File testClassesDirectory;

	private void overrideProperties() throws MojoExecutionException {
		Properties pluginProperties = new Properties();
		pluginProperties.putAll(session.getCurrentProject()
				.getBuildPlugins()
				.stream()
				.filter(p -> p.getId().startsWith(RP_PLUGIN_ID))
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

	@Override
	public void execute() throws MojoExecutionException {
		overrideProperties();
		setupJunit5();
		setupTestNg();
		setupLogbackLogger();
		setupLog4jLogger();
	}
}
