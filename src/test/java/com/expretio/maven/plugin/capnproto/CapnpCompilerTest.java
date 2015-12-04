package com.expretio.maven.plugin.capnproto;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import com.expretio.maven.plugins.capnp.CapnpCompiler;

public class CapnpCompilerTest
{
    private File outputDirectory = new File("target/capnpCompilerTest/output");
    private File schemaBaseDirectory = new File("src/test/resources/schema");
    private File workDirectory = new File("target/capnpCompilerTest/work");

    private String alternativeSchema = "com/expretio/appia/demand/alternative/alternative.capnp";
    private String terminalSchema = "com/expretio/appia/demand/alternative/terminal.capnp";
    private String preferenceListSchema = "com/expretio/appia/demand/profile/preference_list.capnp";

    @Test
    public void test() throws MojoFailureException, MojoExecutionException
    {
        CapnpCompiler compiler = CapnpCompiler.builder()
            .setOutputDirectory(outputDirectory)
            .setSchemaBaseDirectory(schemaBaseDirectory)
            .setWorkDirectory(workDirectory)
            .addSchema(alternativeSchema)
            .addSchema(terminalSchema)
            .addSchema(preferenceListSchema)
            .build();

        compiler.compile();
    }

}
