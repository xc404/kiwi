package com.kiwi.project.tools.codegen.utils;

import cn.hutool.core.util.StrUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenField;
import com.kiwi.project.tools.codegen.entity.JavaType;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;
import jakarta.persistence.Table;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public class JavaFileParser
{

    public static CodeGenVo fromJavaFile(File javaFile) {


        try {
            return fromJavaFile(new FileInputStream(javaFile));
        } catch( FileNotFoundException e ) {
            throw new RuntimeException(e);
        }
    }

    public static CodeGenVo fromJavaFile(InputStream inputStream) {


        JavaParser parser = new JavaParser();

        CompilationUnit compilationUnit = parser.parse(inputStream).getResult().orElseThrow();
        TypeDeclaration<?> type = compilationUnit.getType(0);

        GenEntity genEntity = buildTableInfo(type);
        if( genEntity == null ) {
            return null;
        }


        List<GenField> columns = type.getFields().stream().map(JavaFileParser::buildColumnInfo).filter(Objects::nonNull).toList();

        return new CodeGenVo(genEntity, columns);

    }

    private static GenField buildColumnInfo(FieldDeclaration field) {
        if( field.isStatic() ) {
            return null;
        }
        GenField genField = new GenField();
        genField.setColumnName(field.getVariables().get(0).getNameAsString());
        genField.setJavaType(JavaType.valueOf(field.getElementType().asString()));
        field.getAnnotationByClass(jakarta.persistence.Column.class).ifPresent(annotationExpr -> {
            NormalAnnotationExpr normalAnnotationExpr = annotationExpr.asNormalAnnotationExpr();
            normalAnnotationExpr.getPairs().forEach(memberValuePair -> {
                switch( memberValuePair.getNameAsString() ) {
                    case "name":
                        genField.setColumnName(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "nullable":
                        genField.setRequired(!memberValuePair.getValue().asBooleanLiteralExpr().getValue());
                        break;
                    case "unique":
                        genField.setUnique(memberValuePair.getValue().asBooleanLiteralExpr().getValue());
                        break;
                    case "insertable":
                        genField.setInsertable(memberValuePair.getValue().asBooleanLiteralExpr().getValue());
                        break;
                    case "updatable":
                        genField.setUpdatable(memberValuePair.getValue().asBooleanLiteralExpr().getValue());
                        break;
                    case "precision":
                        genField.setPrecision(memberValuePair.getValue().asIntegerLiteralExpr().asNumber().intValue());
                        break;
                    case "length":
                        genField.setLength(memberValuePair.getValue().asIntegerLiteralExpr().asNumber().intValue());
                        break;
                    case "columnDefinition":
                        genField.setColumnDefinition(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "comment":
                        genField.setColumnComment(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "scale":
                        genField.setScale(memberValuePair.getValue().asIntegerLiteralExpr().asNumber().intValue());
                        break;
                    case "secondPrecision":
                        genField.setSecondPrecision(memberValuePair.getValue().asIntegerLiteralExpr().asNumber().intValue());
                        break;
                }
            });
        });

        if( StringUtils.isBlank(genField.getColumnComment()) ) {
            genField.setColumnComment(field.getComment().map(comment -> comment.getContent().trim()).orElse("").replaceAll("\\*", ""));
        }
        if( StringUtils.isBlank(genField.getColumnName()) ) {
            genField.setColumnName(field.getVariables().get(0).getNameAsString());
        }
        genField.setJavaField(field.getVariables().get(0).getNameAsString());

        return genField;
    }

    private static GenEntity buildTableInfo(TypeDeclaration<?> type) {
        GenEntity genEntity = new GenEntity();
        genEntity.setClassName(type.getName().getId());
        String qualifiedName = type.getFullyQualifiedName().orElse("");
        genEntity.setPackageName(qualifiedName.substring(0, qualifiedName.lastIndexOf(".")));
        AnnotationExpr annotationExpr = type.getAnnotationByClass(Table.class).orElse(null);
        if( annotationExpr != null ) {
            NormalAnnotationExpr normalAnnotationExpr = annotationExpr.asNormalAnnotationExpr();
            normalAnnotationExpr.getPairs().forEach(memberValuePair -> {
                switch( memberValuePair.getNameAsString() ) {

                    case "name":
                        genEntity.setTableName(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "schema":
                        genEntity.setTableSchema(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "catalog":
                        genEntity.setTableCatalog(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                    case "comment":
                        genEntity.setTableComment(memberValuePair.getValue().asStringLiteralExpr().getValue());
                        break;
                }
            });
        }
        if( StringUtils.isBlank(genEntity.getTableComment()) ) {
            genEntity.setTableComment(type.getComment().map(comment -> comment.getContent().trim()).orElse("").replaceAll("\\*", ""));
        }
        if( StringUtils.isBlank(genEntity.getTableName()) ) {
            genEntity.setTableName(StrUtil.lowerFirst(type.getName().getId()));
        }
        return genEntity;
    }


}
