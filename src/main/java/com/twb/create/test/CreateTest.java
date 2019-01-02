package com.twb.create.test;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
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
import java.util.List;
import java.util.Optional;

public class CreateTest {

    private final GenerationOptions options;

    public CreateTest(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

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
        ClassName appClassName = ClassName.get(packageName, options.getAppMainClass());
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

        return JavaPoetUtil.buildJavaFile(resourceTestPackage, testResourceTypeSpec).
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
}
