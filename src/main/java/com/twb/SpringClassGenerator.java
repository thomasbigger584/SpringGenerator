package com.twb;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.squareup.javapoet.JavaFile;
import com.twb.create.*;
import com.twb.create.test.CreateDataUtil;
import com.twb.create.test.CreateTest;
import com.twb.util.GenerationOptions;
import com.twb.util.PathConverter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SpringClassGenerator {

    private static final String MAIN_PATH = "/src/main/java/";
    private static final String TEST_PATH = "/src/test/java/";

    @Parameter(names = "-e", description = "Entity to parse", required = true)
    private List<String> entities = new ArrayList<>();

    @Parameter(names = "--mc", description = "App Main class", required = true)
    private String appMainClass = null;

    @Parameter(names = "-p", converter = PathConverter.class, description = "Path to Project Path")
    private Path projectPath = Paths.get("/Users/thomasbigger/Projects/TradingBotApp/api");

    @Parameter(names = "--ep", description = "Extension Prefix for generated files")
    private String extensionPrefix = "Ext";

    @Parameter(names = "--pn", description = "Package name for generated files")
    private String packageName = "com.twb.tradingbotapp";

    @Parameter(names = "--es", description = "Supports elastic search")
    private boolean supportsElasticSearch = false;

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    @Parameter(names = "--st", description = "Skip Resource Test")
    private boolean skipTest = true;

    public static void main(String... args) {
        SpringClassGenerator scg = new SpringClassGenerator();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(scg)
                .build();
        jCommander.parse(args);

        if (scg.help) {
            jCommander.usage();
            return;
        }

        scg.generate();
    }

    private void generate() {

        for (String entityName : entities) {

            entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

            List<JavaFile> mainJavaFiles = new ArrayList<>();

            GenerationOptions options = new GenerationOptions();
            options.setEntityName(entityName);
            options.setExtensionPrefix(extensionPrefix);
            options.setPackageName(packageName);
            options.setSupportsElasticSearch(supportsElasticSearch);
            options.setAppMainClass(appMainClass);

            mainJavaFiles.add(new CreateRepository(options).create());

            CreateDto createDto = new CreateDto(options);
            mainJavaFiles.add(createDto.create(CreateDto.PREFIX_GET, true));
            mainJavaFiles.add(createDto.create(CreateDto.PREFIX_CREATE, false));
            mainJavaFiles.add(createDto.create(CreateDto.PREFIX_UPDATE, true));

            mainJavaFiles.add(new CreateMapper(options).create());

            if (supportsElasticSearch) {
                mainJavaFiles.add(new CreateSearchRepository(options).create());
            }

            mainJavaFiles.add(new CreateException(options).create());
            mainJavaFiles.add(new CreateService(options).create());
            mainJavaFiles.add(new CreateResource(options).create());

            List<JavaFile> testJavaFiles = new ArrayList<>();
            if (!skipTest) {
                testJavaFiles.add(new CreateDataUtil(options).create());
                testJavaFiles.add(new CreateTest(options).create());
            }

            try {
                String projectPathString = projectPath.toString();
                for (JavaFile mainJavaFile : mainJavaFiles) {
                    mainJavaFile.writeTo(Paths.get(projectPathString + MAIN_PATH));
                }
                for (JavaFile testJavaFile : testJavaFiles) {
                    testJavaFile.writeTo(Paths.get(projectPathString + TEST_PATH));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}