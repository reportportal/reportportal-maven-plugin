package com.epam.reportportal;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "configure", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class ReportPortalInstallMojo extends AbstractMojo {

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	private MavenSession session;

	public void execute() throws MojoExecutionException
	{
		getLog().info( "Got these plugins: " );
		session.getCurrentProject().getBuildPlugins().forEach(p->getLog().info(p.getId()));
	}
}
