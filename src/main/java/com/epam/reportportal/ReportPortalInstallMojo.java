package com.epam.reportportal;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import static java.util.Optional.ofNullable;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ReportPortalInstallMojo extends AbstractMojo {

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	private MavenSession session;

	public void execute() throws MojoExecutionException {
		getLog().info("Got these plugins: ");
		session.getCurrentProject()
				.getBuildPlugins()
				.stream()
				.filter(p -> p.getId().startsWith("org.apache.maven.plugins:maven-surefire-plugin") || p.getId()
						.startsWith("org.apache.maven.plugins:maven-surefire-plugin"))
				.forEach(p -> {
					Xpp3Dom config = ofNullable(p.getConfiguration()).map(c -> (Xpp3Dom) c)
							.orElseGet(() -> new Xpp3Dom("configuration"));
					getLog().info(config.toString());
					getLog().info("Class: " + config.getClass().getName());
					Xpp3Dom argLine = new Xpp3Dom("argLine");
					argLine.setValue("-Dtest.param=TestTest");
					config.addChild(argLine);
					getLog().info(config.toString());
					p.setConfiguration(config);
				});

	}
}
