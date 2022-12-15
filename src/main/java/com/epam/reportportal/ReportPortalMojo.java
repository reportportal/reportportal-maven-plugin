package com.epam.reportportal;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo( name = "ReportPortal")
public class ReportPortalMojo extends AbstractMojo {

	public void execute() throws MojoExecutionException
	{
		getLog().info( "Hello, world." );
	}
}
