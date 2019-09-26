/*
 *  Copyright (c) 2015 ExPretio Technologies, Inc. and contributors
 *  Licensed under the MIT License:
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.expretio.maven.plugins.capnp;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implements a java adapter of capnproto compiler, creating java classes from schema definitions.
 *
 * @see #builder()
 */
public class CapnpCompiler {
    public static Builder builder() {
        return new Builder();
    }

    private final Command command;
    private final List<String> schemas;
    private final boolean verbose;

    /**
     * Constructor.
     */
    private CapnpCompiler(Command command, List<String> schemas, boolean verbose) {
        this.command = command;
        this.schemas = schemas;
        this.verbose = verbose;
    }

    public void compile()
            throws MojoExecutionException {
        for (String schema : schemas) {
            compile(schema);
        }
    }

    // [ Utility methods ]

    private void compile(String schema)
            throws MojoExecutionException {
        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder(command.get(schema))
                            .directory(command.workDirectory);


            Process process = processBuilder.start();

            String packageStr = null;
            String classnameStr = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String s;
                while ((s = reader.readLine()) != null) {
                    String[] parts = s.split("=");
                    if (parts.length != 2) {
                        continue;
                    }
                    if ("Package".equals(parts[0])) {
                        packageStr = parts[1];
                    }
                    if ("Classname".equals(parts[0])) {
                        classnameStr = parts[1];
                    }
                }
            } catch (IOException ignore) {
                System.out.println(ignore);
            }

            int exit = process.waitFor();

            if (exit != 0) {
                throw new MojoExecutionException("Unexpected exit value ( " + exit + " ) while compiling " + schema);
            }

            // Move the file to the correct package
            if (packageStr != null && classnameStr != null) {
                File packageFile = new File(command.outputDirectory, packageStr.replace(".", File.separator));
                packageFile.mkdirs();
                File outputFile = new File(command.outputDirectory, classnameStr + ".java");
                outputFile.renameTo(new File(packageFile, classnameStr + ".java"));
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Cannot compile schema " + schema + ".", e);
        }
    }

    // [ Inner classes ]

    private static class Command {
        private final File outputDirectory;
        private final File schemaDirectory;
        private final File workDirectory;
        private final String capnpExecutable;
        private final String capnpcJavaExecutable;
        private final File capnpJavaSchemaFile;
        private List<File> importDirectories;

        private List<String> base = new ArrayList<>();

        public Command(
                File outputDirectory,
                File schemaDirectory,
                File workDirectory,
                String capnpExecutable,
                String capnpcJavaExecutable,
                File capnpJavaSchemaFile,
                List<File> importDirectories)
                throws MojoExecutionException, MojoFailureException {
            this.outputDirectory = outputDirectory;
            this.schemaDirectory = schemaDirectory;
            this.workDirectory = workDirectory;
            this.capnpExecutable = capnpExecutable;
            this.capnpcJavaExecutable = capnpcJavaExecutable;
            this.capnpJavaSchemaFile = capnpJavaSchemaFile;
            this.importDirectories = importDirectories;

            initialize();
        }

        public List<String> get(String schema) {
            List<String> fullCommand = new ArrayList<>(base);
            fullCommand.add(schema);

            return fullCommand;
        }

        private static File findExecutableOnPath(String name) {
            for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
                File file = new File(dirname, name);
                if (file.isFile() && file.canExecute()) {
                    return file;
                }
            }
            throw new AssertionError("should have found the executable");
        }

        private void initialize()
                throws MojoExecutionException {
            outputDirectory.mkdirs();
            workDirectory.mkdirs();

            try {
                FileUtils.copyDirectoryStructure(schemaDirectory, workDirectory);

                importDirectories.add(new File(findExecutableOnPath(capnpExecutable).getParentFile(), "../include"));
                if (capnpJavaSchemaFile != null) {
                    importDirectories.add(capnpJavaSchemaFile.getParentFile());
                }
                importDirectories.add(schemaDirectory);

                setBase();
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to initialize capnp environment.", e);
            }
        }

        private void setBase()
                throws IOException {
            base.add(capnpExecutable);
            base.add("compile");
            base.add("--verbose");
            base.add("-o" + capnpcJavaExecutable + File.pathSeparator + outputDirectory.getAbsolutePath());

            for (File importDirectory : importDirectories) {
                base.add("-I" + importDirectory.getAbsolutePath());
            }
        }
    }

    public static class Builder {
        private File outputDirectory;
        private File schemaDirectory;
        private File workDirectory;
        private String capnpExecutable;
        private String capnpcJavaExecutable;
        private File capnpJavaSchemaFile;
        private final List<File> importDirectories = new ArrayList<>();
        private final List<String> schemas = new ArrayList<>();
        private boolean verbose = true;

        public CapnpCompiler build()
                throws MojoExecutionException, MojoFailureException {
            validate();

            Command command =
                    new Command(
                            outputDirectory,
                            schemaDirectory,
                            workDirectory,
                            capnpExecutable,
                            capnpcJavaExecutable,
                            capnpJavaSchemaFile,
                            importDirectories);

            return new CapnpCompiler(command, schemas, verbose);
        }

        public Builder setOutputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;

            return this;
        }

        public Builder setSchemaDirectory(File schemaDirectory) {
            this.schemaDirectory = schemaDirectory;

            return this;
        }

        public Builder setWorkDirectory(File workDirectory) {
            this.workDirectory = workDirectory;

            return this;
        }

        public Builder setCapnpExecutable(String capnpExecutable) {
            this.capnpExecutable = capnpExecutable;

            return this;
        }

        public Builder setCapnpcJavaExecutable(String capnpcJavaExecutable) {
            this.capnpcJavaExecutable = capnpcJavaExecutable;

            return this;
        }

        public Builder setCapnpJavaSchemaFile(File capnpJavaSchemaFile) {
            this.capnpJavaSchemaFile = capnpJavaSchemaFile;

            return this;
        }

        public Builder addImportDirectory(File importDirectory) {
            importDirectories.add(importDirectory);

            return this;
        }

        public Builder addImportDirectories(Collection<File> importDirectories) {
            this.importDirectories.addAll(importDirectories);

            return this;
        }

        public Builder addSchema(String schema) {
            schemas.add(schema);

            return this;
        }

        public Builder addSchemas(Collection<String> schemas) {
            this.schemas.addAll(schemas);

            return this;
        }

        public Builder setVerbose(boolean value) {
            this.verbose = value;

            return this;
        }

        private void validate()
                throws MojoFailureException {
            validate(outputDirectory, "Output directory");
            validate(schemaDirectory, "Schema base directory");
            validate(workDirectory, "Working directory");

            validate(capnpExecutable, "capnpn file");
            validate(capnpcJavaExecutable, "capnpnc java file");

            for (File importDirectory : importDirectories) {
                validate(importDirectory, "Import directory");
            }

            if (schemas.isEmpty()) {
                throw new MojoFailureException("At least one schema file must be specified.");
            }
        }

        private void validate(File file, String name)
                throws MojoFailureException {
            if (file == null) {
                throw new MojoFailureException(name + " is mandatory.");
            }
        }

        private void validate(String file, String name)
                throws MojoFailureException {
            if (file == null) {
                throw new MojoFailureException(name + " is mandatory.");
            }
        }
    }
}

