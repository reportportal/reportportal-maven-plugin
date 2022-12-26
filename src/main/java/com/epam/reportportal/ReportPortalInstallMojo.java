package com.epam.reportportal;

import com.epam.reportportal.utils.properties.PropertiesLoader;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ReportPortalInstallMojo extends AbstractMojo {

	private static final String RP_PLUGIN_ID = "com.epam.reportportal:reportportal-maven-plugin";

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(property = "project.build.directory", defaultValue = "${project.build.directory}", readonly = true)
	private String buildDirectoryPath;

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
		Path propertyFilePath = Paths.get(buildDirectoryPath, "classes", propertyFilePathStr);

		Properties fsProperties = new Properties();
		try {
			fsProperties.load(Files.newInputStream(propertyFilePath));
		} catch (FileNotFoundException e) {
			// Override property file then
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to read Report Portal property file", e);
		}
		fsProperties.putAll(pluginProperties);
		try {
			fsProperties.store(Files.newOutputStream(propertyFilePath), "Report Portal generated properties");
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to write Report Portal property file", e);
		}
	}

	private void generateJunit5files(){
		// TODO: implement
	}

	private void generateTestNgFiles(){
		// TODO: implement
	}

	@Override
	public void execute() throws MojoExecutionException {
		overrideProperties();
	}
}
