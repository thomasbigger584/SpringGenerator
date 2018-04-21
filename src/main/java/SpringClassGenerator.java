import com.squareup.javapoet.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.lang.model.element.Modifier;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SpringClassGenerator {

    public static void main(String... args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter project location: ");
//        String projectLocation = scanner.next();
        String projectLocation = "/Users/thomasbigger/Desktop/projects/backend/chatbot-backend";
        String javaSourceLocation = projectLocation + "/src/main/java/";

        System.out.println("Enter package name: ");
//        String packageName = scanner.next();
        String packageName = "com.pa.twb";

        System.out.println("Enter entity name: ");
        String entityName = scanner.next();

        entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

        List<JavaFile> javaFiles = new ArrayList<>();

        javaFiles.add(createJpaEntity(packageName, entityName));
        javaFiles.add(createRepository(packageName, entityName));
        javaFiles.add(createMapper(packageName, entityName));
        javaFiles.add(createService(packageName, entityName));
        javaFiles.add(createResource(packageName, entityName));

        Path outputFileLocation = Paths.get(javaSourceLocation);

        try {
            for (JavaFile javaFile : javaFiles) {
                javaFile.writeTo(outputFileLocation);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JavaFile createJpaEntity(String packageName, String entityName) {

        String domainPackage = packageName + ".domain";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityName).

                addModifiers(Modifier.PUBLIC, Modifier.FINAL).

                addSuperinterface(Serializable.class).
                addField(FieldSpec.
                        builder(TypeName.LONG, "serialVersionUID",
                                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).
                        initializer("1").
                        build()).

                addAnnotation(Entity.class).
                addAnnotation(AnnotationSpec.builder(Table.class).
                        addMember("name", "\"" + entityName.toLowerCase() + "\"").build()).
                addAnnotation(AnnotationSpec.builder(Cache.class).
                        addMember("usage", "$T.$L", CacheConcurrencyStrategy.class,
                                CacheConcurrencyStrategy.NONSTRICT_READ_WRITE.name()).
                        build()).

                build();

        return buildJavaFile(domainPackage, jpaEntityTypeSpec);
    }

    private static JavaFile createRepository(String packageName, String entityName) {

        String repositoryPackage = packageName + ".repository";
        String entityRepository = entityName + "Repository";

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(JpaRepository.class),
                        ClassName.get(packageName + ".domain", entityName), ClassName.get(Long.class)))
                .build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private static JavaFile createMapper(String packageName, String entityName) {

        String mapperPackage = packageName + ".service.mapper";
        String entityMapper = entityName + "Mapper";

        TypeSpec mapperTypeSpec = TypeSpec.interfaceBuilder(entityMapper)
                .addModifiers(Modifier.PUBLIC)
                .build();

        return buildJavaFile(mapperPackage, mapperTypeSpec);
    }

    private static JavaFile createService(String packageName, String entityName) {

        String repositoryPackage = packageName + ".service";
        String entityService = entityName + "Service";

        final ClassName repositoryClassName =
                ClassName.get(packageName + ".repository", entityName + "Repository");
        final String repositoryVarName = entityName.toLowerCase() + "Repository";

        FieldSpec repositoryField = FieldSpec.builder(repositoryClassName,
                repositoryVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        final ClassName mapperClassName =
                ClassName.get(packageName + ".service.mapper", entityName + "Mapper");
        final String mapperVarName = entityName.toLowerCase() + "Mapper";

        FieldSpec mapperField = FieldSpec.builder(mapperClassName,
                mapperVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addParameter(repositoryClassName, repositoryVarName).
                addParameter(mapperClassName, mapperVarName).
                addStatement("this." + repositoryVarName + " = " + repositoryVarName).
                addStatement("this." + mapperVarName + " = " + mapperVarName).
                build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityService).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(Service.class).
                addAnnotation(Transactional.class).
                addField(repositoryField).
                addField(mapperField).
                addMethod(constructor).
                build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private static JavaFile createResource(String packageName, String entityName) {

        final String repositoryPackage = packageName + ".web.rest";
        final String entityRepository = entityName + "Resource";

        final ClassName serviceClassName =
                ClassName.get(packageName + ".service", entityName + "Service");
        final String serviceVarName = entityName.toLowerCase() + "Service";

        FieldSpec serviceField = FieldSpec.builder(serviceClassName,
                serviceVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addParameter(serviceClassName, serviceVarName).
                addStatement("this." + serviceVarName + " = " + serviceVarName).
                build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(RestController.class).
                addAnnotation(AnnotationSpec.builder(RequestMapping.class).
                        addMember("value", "\"/api/" + entityName.toLowerCase() + "\"").
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
                build();
    }
}
