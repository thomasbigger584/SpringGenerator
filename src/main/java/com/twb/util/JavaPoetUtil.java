package com.twb.util;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

public class JavaPoetUtil {

    public static JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("    ").
                build();
    }
}
