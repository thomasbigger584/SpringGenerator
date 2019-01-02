package com.twb;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.squareup.javapoet.*;
import com.twb.create.*;
import com.twb.util.GenerationOptions;
import com.twb.util.PathConverter;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.lang.model.element.Modifier;
import javax.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpringClassGenerator {

    @Parameter(names = "-e", description = "Entity to parse", required = true)
    private List<String> entities = new ArrayList<>();

    @Parameter(names = "--mc", description = "App Main class", required = true)
    private String appMainClass = null;

    @Parameter(names = "-p", converter = PathConverter.class, description = "Path to Project Path")
    private Path projectPath = Paths.get("/Users/thomasbigger/Desktop/projects/backend/leep-platform/leep-tickets");

    @Parameter(names = "--ep", description = "Extension Prefix for generated files")
    private String extensionPrefix = "Ext";

    @Parameter(names = "--pn", description = "Package name for generated files")
    private String packageName = "com.pa.twb";

    @Parameter(names = "--es", description = "Supports elastic search")
    private boolean supportsElasticSearch = false;

    @Parameter(names = "-d", description = "Turn on debugging")
    private boolean isDebug = false;

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    @Parameter(names = "--st", description = "Skip Resource Test")
    private boolean skipTest = false;

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
                testJavaFiles.add(createDataUtil(entityName));
                testJavaFiles.add(createTest(entityName));
            }

            try {
                for (JavaFile mainJavaFile : mainJavaFiles) {
                    mainJavaFile.writeTo(Paths.get(projectPath.toString() + "/src/main/java/"));
                }
                for (JavaFile testJavaFile : testJavaFiles) {
                    testJavaFile.writeTo(Paths.get(projectPath.toString() + "/src/test/java/"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private JavaFile createDataUtil(String entityName) {

        final String resourceTestPackage = packageName + ".web.rest." + extensionPrefix.toLowerCase() + "." + entityName.toLowerCase();
        final String entityDataUtilResource = entityName + "DataUtil";

        FieldSpec defaultValueFieldSpec = FieldSpec.builder(String.class, "DEFAULT_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).
                initializer("\"CCCCCCCCCC\"").build();
        FieldSpec updatedValueFieldSpec = FieldSpec.builder(String.class, "UPDATED_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).
                initializer("\"DDDDDDDDD\"").build();

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);

        String dtoPackage = packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase();
        final ClassName createEntityClassName = ClassName.get(dtoPackage, "Create" + entityName + "DTO");
        final ClassName updateEntityClassName = ClassName.get(dtoPackage, "Update" + entityName + "DTO");

        MethodSpec createEntityWithObjectMethodSpec = MethodSpec.methodBuilder("com/twb/create" + entityName + "Entity").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                returns(entityClassName).
                addParameter(EntityManager.class, "em").
                addParameter(Object.class, "parent").
                addParameter(Boolean.class, "deleted").
                addStatement("$T entity = new $T()", entityClassName, entityClassName).
                addComment("entity.setParent(parent)").
                addComment("entity.setDeleted(deleted)").
                addComment("entity.setName(DEFAULT_NAME)").
                addStatement("em.persist(entity)").
                addComment("parent.getEntities().add(entity)").
                addStatement("return entity").
                build();

        MethodSpec createEntityMethodSpec = MethodSpec.methodBuilder("com/twb/create" + entityName + "Entity").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                returns(entityClassName).
                addParameter(EntityManager.class, "em").
                addParameter(Boolean.class, "deleted").
                addStatement("$T entity = new $T()", Object.class, Object.class).
                addStatement("return com.twb.create" + entityName + "Entity(em, entity, deleted)").
                build();

        MethodSpec createCreateDtoMethodSpec = MethodSpec.methodBuilder("createCreate" + entityName + "EntityDTO").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                addParameter(Long.class, "parentId").
                returns(createEntityClassName).
                addStatement("$T createEntityDto = new $T()", createEntityClassName, createEntityClassName).
                addComment("createEntityDto.setParentId(parentId)").
                addComment("createEntityDto.setName(DEFAULT_NAME)").
                addStatement("return createEntityDto").
                build();

        MethodSpec createUpdateDtoMethodSpec = MethodSpec.methodBuilder("createUpdate" + entityName + "EntityDTO").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                addParameter(Long.class, "id").
                returns(updateEntityClassName).
                addStatement("$T updateEntityDto = new $T()", updateEntityClassName, updateEntityClassName).
                addStatement("updateEntityDto.setId(id)").
                addComment("updateEntityDto.setName(UPDATED_NAME)").
                addStatement("return updateEntityDto").
                build();

        TypeSpec testResourceTypeSpec = TypeSpec.classBuilder(entityDataUtilResource).
                addModifiers(Modifier.PUBLIC).
                addField(defaultValueFieldSpec).
                addField(updatedValueFieldSpec).
                addMethod(createEntityWithObjectMethodSpec).
                addMethod(createEntityMethodSpec).
                addMethod(createCreateDtoMethodSpec).
                addMethod(createUpdateDtoMethodSpec).
                build();

        return buildJavaFile(resourceTestPackage, testResourceTypeSpec);
    }

    private JavaFile createTest(String entityName) {

        final String resourceTestPackage = packageName + ".web.rest." + extensionPrefix.toLowerCase() + "." + entityName.toLowerCase();
        final String entityTestResource = extensionPrefix + entityName + "ResourceIntTest";

        final ClassName repoClassName =
                ClassName.get(packageName + ".repository." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Repository");
        final String repoVarName = extensionPrefix.toLowerCase() + entityName + "Repository";
        FieldSpec repoFieldSpec = FieldSpec.builder(repoClassName, repoVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName serviceClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Service");
        final String serviceVarName = extensionPrefix.toLowerCase() + entityName + "Service";
        FieldSpec serviceFieldSpec = FieldSpec.builder(serviceClassName, serviceVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName jacksonClassName = ClassName.get(MappingJackson2HttpMessageConverter.class);
        final String jacksonVarName = "jacksonMessageConverter";
        FieldSpec jacksonFieldSpec = FieldSpec.builder(jacksonClassName, jacksonVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName pageableClassName = ClassName.get(PageableHandlerMethodArgumentResolver.class);
        final String pageableVarName = "pageableArgumentResolver";
        FieldSpec pageableFieldSpec = FieldSpec.builder(pageableClassName, pageableVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        ClassName exceptionClassName = ClassName.get(packageName + ".web.rest.errors", "ExceptionTranslator");
        final String exceptionVarName = "exceptionTranslator";
        FieldSpec exceptionFieldSpec = FieldSpec.builder(exceptionClassName, exceptionVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName entityManagerClassName = ClassName.get(EntityManager.class);
        final String entityManagerVarName = "em";
        FieldSpec entityManagerFieldSpec = FieldSpec.builder(entityManagerClassName, entityManagerVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName restMvcClassName = ClassName.get(MockMvc.class);
        final String restMvcVarName = "rest" + entityName + "MockMvc";
        FieldSpec restMvcFieldSpec = FieldSpec.builder(restMvcClassName, restMvcVarName, Modifier.PRIVATE).build();

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);
        final ClassName resourceClassName =
                ClassName.get(packageName + ".web.rest." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Resource");
        final String resourceVarName = extensionPrefix.toLowerCase() + entityName + "Resource";

        ClassName testUtilClassName = ClassName.get(packageName + ".web.rest", "TestUtil");
        MethodSpec setupMethodSpec = MethodSpec.methodBuilder("setup").
                addAnnotation(Before.class).
                addModifiers(Modifier.PUBLIC).
                addStatement("$T.initMocks(this)", MockitoAnnotations.class).
                addStatement("final $T " + resourceVarName + " = new $T(" + serviceVarName + ")", resourceClassName, resourceClassName).
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + " = $T.standaloneSetup(" + resourceVarName + ")\n", MockMvcBuilders.class).
                        indent().add(".setCustomArgumentResolvers(pageableArgumentResolver)\n").
                        add(".setConversionService(createFormattingConversionService())\n").
                        add(".setMessageConverters(jacksonMessageConverter).build();\n").
                        unindent().build()).
                build();

        /*
         * Tests
         * ******************************************************************
         */

        String baseApiUrl = "/api/" + extensionPrefix.toLowerCase() + "-" + getUrlPath(entityName);
        ParameterizedTypeName listEntityTypeName = ParameterizedTypeName.get(ClassName.get(List.class), entityClassName);

        MethodSpec testEntityCreateMethodSpec = MethodSpec.methodBuilder("testCreate" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeCreate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createCreate" + entityName + "EntityDTO(1L)))) //update\n", testUtilClassName).
                        add(".andDo(print())\n").
                        add(".andExpect(status().isCreated());\n\n").
                        unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeCreate + 1)").
                addStatement("$T test = list.get(list.size() - 1)", entityClassName).
                addComment("assertThat(test.getName()).isEqualTo(DEFAULT_NAME)").
                build();

        ClassName entityException = ClassName.get(packageName + ".web.rest.errors." + extensionPrefix.toLowerCase(), entityName + "NotFoundException");
        MethodSpec testEntityCreateInvalidParentMethodSpec = MethodSpec.methodBuilder("testCreate" + entityName + "InvalidParent").
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeCreate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createCreate" + entityName + "EntityDTO($T.MAX_VALUE))))\n", testUtilClassName, Long.class).
                        add(".andExpect(status().isCreated())).\n").
                        unindent().add("hasCause(new $T());\n\n", entityException).unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeCreate)").
                build();

        MethodSpec tesUpdateEntityMethodSpec = MethodSpec.methodBuilder("testUpdate" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeUpdate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(put(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createUpdate" + entityName + "EntityDTO(1L)))) //update\n", testUtilClassName).
                        add(".andDo(print())\n").
                        add(".andExpect(status().isOk());\n\n").
                        unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeUpdate)").
                addStatement("$T test = list.get(list.size() - 1)", entityClassName).
                addComment("assertThat(test.getName()).isEqualTo(UPDATED_NAME)").
                build();

        MethodSpec updateNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testUpdateNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addStatement("int databaseSizeBeforeUpdate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(put(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createUpdate" + entityName + "EntityDTO($T.MAX_VALUE))))\n", testUtilClassName, Long.class).
                        add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n\n", entityException).unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeUpdate)").
                build();

        MethodSpec testGetEntityMethodSpec = MethodSpec.methodBuilder("testGet" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/{id}\", 1L)) //update\n").
                        indent().add(".andExpect(status().isOk())\n").
                        add(".andDo(print())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.id\").value(1L)); //update\n").
                        add("// .andExpect(jsonPath(\"$$.name\").value(DEFAULT_NAME));\n").
                        unindent().build()).
                build();

        MethodSpec testGetNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testGetNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        MethodSpec testGetAllEntityMethodSpec = MethodSpec.methodBuilder("testGetAll" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "?sort=id,desc\"))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.[*].id\").value(hasItem(1L))); //update\n").
                        add("// .andExpect(jsonPath(\"$$.[*].name\").value(hasItem(DEFAULT_NAME)));\n").
                        unindent().build()).
                build();

        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);
        MethodSpec testDeleteEntityMethodSpec = MethodSpec.methodBuilder("testDelete" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        addStatement("int databaseSizeBeforeDelete = " + repoVarName + ".findAll().size()").
                        add("this." + restMvcVarName + ".perform(delete(\"" + baseApiUrl + "/{id}\", 1L))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isNoContent());\n\n").
                        unindent().addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                        add("assertThat(list).hasSize(databaseSizeBeforeDelete - 1);\n\n").
                        add("$T test = " + repoVarName + ".findById(1L); // update\n", optionalEntityTypeName).
                        addStatement("assertThat(test.isPresent()).isFalse()").build()).
                build();

        MethodSpec testDeleteNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testDeleteNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(delete(\"" + baseApiUrl + "/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isNoContent())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        MethodSpec testGetAllDeletedEntityMethodSpec = MethodSpec.methodBuilder("testGetAllDeleted" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/deleted?sort=id,desc\"))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.[*].id\").value(hasItem(1L))); //update\n").
                        add("// .andExpect(jsonPath(\"$$.[*].name\").value(hasItem(DEFAULT_NAME)));\n").
                        unindent().build()).
                build();


        MethodSpec testRecoverEntityMethodSpec = MethodSpec.methodBuilder("testRecoverDeleted" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "/recover/{id}\", 1L)) // update\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.id\").value(1L)); //update\n").
                        add("// .andExpect(jsonPath(\"$$.name\").value(DEFAULT_NAME));\n").
                        unindent().build()).
                build();

        MethodSpec testRecoverNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testRecoverNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "/recover/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        ClassName securityBeanConfigClassName = ClassName.get(packageName + ".config", "SecurityBeanOverrideConfiguration");
        ClassName appClassName = ClassName.get(packageName, appMainClass);
        TypeSpec testResourceTypeSpec = TypeSpec.classBuilder(entityTestResource).
                addModifiers(Modifier.PUBLIC).
                addJavadoc("TODO: Update DTOs for relevant data and adjust tests for data accordingly.\n").
                addAnnotation(AnnotationSpec.builder(RunWith.class).
                        addMember("value", "$T.class", ClassName.get(SpringRunner.class)).
                        build()).
                addAnnotation(AnnotationSpec.builder(SpringBootTest.class)
                        .addMember("classes", "{$T.class, $T.class}", securityBeanConfigClassName, appClassName).build()).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                addField(repoFieldSpec).
                addField(serviceFieldSpec).
                addField(jacksonFieldSpec).
                addField(pageableFieldSpec).
                addField(exceptionFieldSpec).
                addField(entityManagerFieldSpec).
                addField(restMvcFieldSpec).
                addMethod(setupMethodSpec).
                addMethod(testEntityCreateMethodSpec).
                addMethod(testEntityCreateInvalidParentMethodSpec).
                addMethod(tesUpdateEntityMethodSpec).
                addMethod(updateNonExistingEntityMethodSpec).
                addMethod(testGetEntityMethodSpec).
                addMethod(testGetNonExistingEntityMethodSpec).
                addMethod(testGetAllEntityMethodSpec).
                addMethod(testDeleteEntityMethodSpec).
                addMethod(testDeleteNonExistingEntityMethodSpec).
                addMethod(testGetAllDeletedEntityMethodSpec).
                addMethod(testRecoverEntityMethodSpec).
                addMethod(testRecoverNonExistingEntityMethodSpec).
                build();

        return buildJavaFile(resourceTestPackage, testResourceTypeSpec).
                toBuilder().
                addStaticImport(testUtilClassName, "createFormattingConversionService").
                addStaticImport(ClassName.get(resourceTestPackage, entityName + "DataUtil"), "*").
                addStaticImport(ClassName.get(Assertions.class), "*").
                addStaticImport(ClassName.get(Matchers.class), "hasItem").
                addStaticImport(ClassName.get(MockMvcRequestBuilders.class), "*").
                addStaticImport(ClassName.get(MockMvcResultMatchers.class), "*").
                addStaticImport(ClassName.get(MockMvcResultHandlers.class), "print").
                build();
    }

    private String getUrlPath(String entityName) {
        String resourceUriName = "";
        String[] words = entityName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for (int index = 0; index < words.length; index++) {
            resourceUriName = resourceUriName.concat(words[index]);
            if (index < words.length - 1) {
                resourceUriName = resourceUriName.concat("-");
            }
        }
        return resourceUriName.toLowerCase();
    }

    private JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("    ").
                build();
    }
}