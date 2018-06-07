import com.squareup.javapoet.*;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SpringClassGenerator {

    private static final String PROJECT_LOCATION = "/Users/thomasbigger/Desktop/projects/backend/skylark-backend";
    private static final String JAVA_SRC_LOCATION = PROJECT_LOCATION + "/src/main/java/";
    private static final String PACKAGE_NAME = "com.pa.twb";
    private static final String EXTENSION_PREFIX = "Ext";
    private static final boolean SUPPORTS_ELASTIC_SEARCH = true;

    public static void main(String... args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter entity name: ");
        String entityName = scanner.next();

        entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

        List<JavaFile> javaFiles = new ArrayList<>();

        javaFiles.add(createRepository(entityName));
        javaFiles.add(createMapper(entityName));
        if (SUPPORTS_ELASTIC_SEARCH) {
            javaFiles.add(createSearchRespository(entityName));
        }
        javaFiles.add(createService(entityName));
        javaFiles.add(createResource(entityName));

        Path outputFileLocation = Paths.get(JAVA_SRC_LOCATION);

        try {
            for (JavaFile javaFile : javaFiles) {
                javaFile.writeTo(outputFileLocation);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JavaFile createRepository(String entityName) {

        String repositoryPackage = PACKAGE_NAME + ".repository." + EXTENSION_PREFIX.toLowerCase();
        String entityRepository = EXTENSION_PREFIX + entityName + "Repository";

        final ClassName superRepoClassName =
                ClassName.get(PACKAGE_NAME + ".repository", entityName + "Repository");

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superRepoClassName)
                .addAnnotation(Repository.class)
                .build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private static JavaFile createMapper(String entityName) {

        String mapperPackage = PACKAGE_NAME + ".service.mapper." + EXTENSION_PREFIX.toLowerCase();
        String entityMapper = EXTENSION_PREFIX + entityName + "Mapper";

        TypeSpec mapperTypeSpec = TypeSpec.interfaceBuilder(entityMapper)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Mapper.class).
                        addMember("componentModel", "\"spring\"").
                        addMember("uses", "{,}").
                        build())
                .build();

        return buildJavaFile(mapperPackage, mapperTypeSpec);
    }

    private static JavaFile createSearchRespository(String entityName) {

        String repositoryPackage = PACKAGE_NAME + ".repository.search." + EXTENSION_PREFIX.toLowerCase();
        String entityRepository = EXTENSION_PREFIX + entityName + "SearchRepository";

        final ClassName superRepoClassName =
                ClassName.get(PACKAGE_NAME + ".repository.search", entityName + "SearchRepository");

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superRepoClassName)
                .addAnnotation(Repository.class)
                .build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private static JavaFile createService(String entityName) {

        String servicePackage = PACKAGE_NAME + ".service." + EXTENSION_PREFIX.toLowerCase();
        String entityService = EXTENSION_PREFIX + entityName + "Service";

        final ClassName repositoryClassName =
                ClassName.get(PACKAGE_NAME + ".repository." + EXTENSION_PREFIX.toLowerCase(), EXTENSION_PREFIX + entityName + "Repository");
        final String repositoryVarName = EXTENSION_PREFIX.toLowerCase() + entityName + "Repository";

        FieldSpec repositoryField = FieldSpec.builder(repositoryClassName,
                repositoryVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        final ClassName repositorySearchClassName =
                ClassName.get(PACKAGE_NAME + ".repository.search." + EXTENSION_PREFIX.toLowerCase(), EXTENSION_PREFIX + entityName + "SearchRepository");
        final String repositorySearchVarName = EXTENSION_PREFIX.toLowerCase() + entityName + "SearchRepository";

        FieldSpec repositorySearchField = FieldSpec.builder(repositorySearchClassName,
                repositorySearchVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        final ClassName mapperClassName =
                ClassName.get(PACKAGE_NAME + ".service.mapper." + EXTENSION_PREFIX.toLowerCase(), EXTENSION_PREFIX + entityName + "Mapper");
        final String mapperVarName = EXTENSION_PREFIX.toLowerCase() + entityName + "Mapper";

        FieldSpec mapperField = FieldSpec.builder(mapperClassName,
                mapperVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addParameter(repositoryClassName, repositoryVarName);

        String superStatement = "super(%s)";
        String parameters = repositoryVarName + ((SUPPORTS_ELASTIC_SEARCH) ? ", " + repositorySearchVarName : "");
        superStatement = String.format(superStatement, parameters);
        constructorBuilder.addStatement(superStatement);


        if (SUPPORTS_ELASTIC_SEARCH) {
            constructorBuilder.addParameter(repositorySearchClassName, repositorySearchVarName).
                    addStatement("this." + repositorySearchVarName + " = " + repositorySearchVarName);
        }

        constructorBuilder.addParameter(mapperClassName, mapperVarName).
                addStatement("this." + repositoryVarName + " = " + repositoryVarName).
                addStatement("this." + mapperVarName + " = " + mapperVarName);


        MethodSpec constructor = constructorBuilder.build();

        final ClassName superServiceClassName =
                ClassName.get(PACKAGE_NAME + ".service", entityName + "Service");

        TypeSpec.Builder jpaEntityTypeSpecBuilder = TypeSpec.classBuilder(entityService).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(Service.class).
                addAnnotation(Transactional.class).
                superclass(superServiceClassName).
                addField(repositoryField).
                addField(mapperField).
                addMethod(constructor);

        if (SUPPORTS_ELASTIC_SEARCH) {
            jpaEntityTypeSpecBuilder.addField(repositorySearchField);
        }

        TypeSpec jpaEntityTypeSpec = jpaEntityTypeSpecBuilder.build();

        return buildJavaFile(servicePackage, jpaEntityTypeSpec);
    }

    private static JavaFile createResource(String entityName) {

        final String repositoryPackage = PACKAGE_NAME + ".web.rest." + EXTENSION_PREFIX.toLowerCase();
        final String entityRepository = EXTENSION_PREFIX + entityName + "Resource";

        final ClassName serviceClassName =
                ClassName.get(PACKAGE_NAME + ".service.ext", "Ext" + entityName + "Service");
        final String serviceVarName = entityName.toLowerCase() + "Service";

        FieldSpec serviceField = FieldSpec.builder(serviceClassName,
                serviceVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addParameter(serviceClassName, serviceVarName).
                addStatement("this." + serviceVarName + " = " + serviceVarName).
                build();

        String resourceUriName = "";
        String[] words = entityName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for (int index = 0; index < words.length; index++) {
            resourceUriName = resourceUriName.concat(words[index]);
            if (index < words.length - 1) {
                resourceUriName = resourceUriName.concat("-");
            }
        }

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(RestController.class).
                addAnnotation(AnnotationSpec.builder(RequestMapping.class).
                        addMember("value", "\"/api/ext-" + resourceUriName.toLowerCase() + "\"").
                        build()).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                addField(serviceField).
                addMethod(constructor).
                build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private static JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("\t").
                build();
    }
}
