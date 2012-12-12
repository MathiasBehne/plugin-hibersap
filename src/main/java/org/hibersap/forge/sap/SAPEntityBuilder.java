/*
 * Copyright (C) 2012 akquinet AG
 *
 * This file is part of the Forge Hibersap Plugin.
 *
 * The Forge Hibersap Plugin is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * The Forge Hibersap Plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with the Forge Hibersap Plugin. If not, see <http://www.gnu.org/licenses/>.
 */

package org.hibersap.forge.sap;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import org.hibersap.HibersapException;
import org.hibersap.annotations.Bapi;
import org.hibersap.annotations.BapiStructure;
import org.hibersap.annotations.Export;
import org.hibersap.annotations.Import;
import org.hibersap.annotations.Parameter;
import org.hibersap.annotations.ParameterType;
import org.hibersap.annotations.Table;
import org.hibersap.mapping.model.BapiMapping;
import org.hibersap.mapping.model.FieldMapping;
import org.hibersap.mapping.model.ParameterMapping;
import org.hibersap.mapping.model.ParameterMapping.ParamType;
import org.hibersap.mapping.model.StructureMapping;
import org.hibersap.mapping.model.TableMapping;
import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.Annotation;
import org.jboss.forge.parser.java.Field;
import org.jboss.forge.parser.java.JavaClass;
import org.jboss.forge.parser.java.Method;
import org.jboss.forge.parser.java.util.Refactory;

/**
 * Builds a SAPEntity
 * 
 * @author Max Schwaab
 *
 */
public class SAPEntityBuilder {

	/** The SAPEntity **/
	private SAPEntity entity;

	/**
	 * Creates a new SAP entity from the given BAPI function mapping  with the given class name and Java package
	 * 
	 * @param className - the class name
	 * @param javaPackage - the Java package
	 * @param functionMapping - the BAPI function mapping
	 */
	public void createNew(final String className, final String javaPackage, final BapiMapping functionMapping) {
		final String bapiName = functionMapping.getBapiName();

		final Set<ParameterMapping> importParams = functionMapping.getImportParameters();
		final Set<ParameterMapping> exportParams = functionMapping.getExportParameters();
		final Set<TableMapping> tableParams = functionMapping.getTableParameters();

		final JavaClass bapiClass = createJavaClass(className, javaPackage);

		// Adding BAPI Annotation
		final Annotation<JavaClass> bapiAnno = bapiClass.addAnnotation(Bapi.class);
		bapiAnno.setStringValue(bapiName);
		//lombok annotations
		bapiClass.addAnnotation(lombok.Data.class);
		bapiClass.addAnnotation(lombok.ToString.class);
		bapiClass.addAnnotation(lombok.RequiredArgsConstructor.class);

		//because camel-hibersap needs it:
		bapiClass.addInterface(Serializable.class);
		
		
		this.entity = new SAPEntity(bapiClass);

		//done by lombok
//		createConstructor(bapiClass, importParams);
		createParameters(bapiClass, importParams, javaPackage, Import.class);
		createParameters(bapiClass, exportParams, javaPackage, Export.class);
		createParameters(bapiClass, tableParams, javaPackage, Table.class);

		//done by lombok
//		Refactory.createToStringFromFields(bapiClass);
	}

	/**
	 * Creates an empty Java class with the given class name and Java package 
	 * 
	 * @param className - the class name
	 * @param javaPackage - the Java package
	 * @return the created Java class
	 */
	private JavaClass createJavaClass(final String className, final String javaPackage) {
		final JavaClass javaClass = JavaParser.create(JavaClass.class);

//		javaClass.addField("/* \n * Generated by JBoss Forge Hibersap plugin\n */");//TODO Find a correct method to add a comment to the generated class
		javaClass.setPublic();
		javaClass.setName(className);
		javaClass.setPackage(javaPackage);

		return javaClass;
	}

	/**
	 * Creates a constructor for the given BAPI class based on the given import parameters 
	 * 
	 * @param bapiClass - the BAPI class
	 * @param importParams - the import parameters
	 */
	private void createConstructor(final JavaClass bapiClass, final Set<ParameterMapping> importParams) {
		final StringBuilder parameterBuilder = new StringBuilder();
		final StringBuilder bodyBuilder = new StringBuilder();

		for (final ParameterMapping parameterMapping : importParams) {
			final String parameterFieldName = parameterMapping.getJavaName().substring(1);
			final Class<?> clazz = parameterMapping.getAssociatedType();
			final String parameterType;

			if (clazz != null) {
				parameterType = clazz.getSimpleName();
			} else {
				parameterType = SAPEntityBuilder.convertFieldNameToClassName(parameterMapping.getJavaName());
			}

			parameterBuilder.append("final ");
			parameterBuilder.append(parameterType);
			parameterBuilder.append(" ");
			parameterBuilder.append(parameterFieldName);
			parameterBuilder.append(", ");

			bodyBuilder.append("this.");
			bodyBuilder.append(parameterMapping.getJavaName());
			bodyBuilder.append(" = ");
			bodyBuilder.append(parameterFieldName);
			bodyBuilder.append(";\n");
		}

		final Method<JavaClass> constructor = bapiClass.addMethod();
		
		constructor.setPublic();
		constructor.setConstructor(true);

		if(!importParams.isEmpty()) {
			final String temp = parameterBuilder.toString();
			final String parameters = temp.substring(0, temp.length() - 2);
			
			constructor.setParameters(parameters);
			constructor.setBody(bodyBuilder.toString());
		}
	}

	/**
	 * Creates the field, structure and table parameters for a given BAPI class with the given annotation
	 * 
	 * @param bapiClass - the BAPI class
	 * @param params - the parameter list set
	 * @param javaPackage - the Java package
	 * @param annotationClass - the annotation needed for given parameter type
	 */
	private void createParameters(final JavaClass bapiClass, final Set<? extends ParameterMapping> params,
			final String javaPackage, final Class<? extends java.lang.annotation.Annotation> annotationClass) {
		for (final ParameterMapping param : params) {
			final String paramName = param.getJavaName();
			final ParamType paramType = param.getParamType();
			final Class<?> associatedType = param.getAssociatedType();
			final Field<JavaClass> field;

			if (paramType == ParamType.FIELD) {
				field = createSimpleField(paramName, associatedType.getName(), bapiClass);
			} else {
				final JavaClass structureClass = createStructureClass(javaPackage, param);
				final String structureClassName = structureClass.getName();

				this.entity.getStructureClasses().add(structureClass);

				switch (paramType) {
				case STRUCTURE:
					field = createSimpleField(paramName, structureClassName, bapiClass);
					break;
				case TABLE:
					//Check for bapiClass.hasImport(List.class) not necessary, because it is done in addImport()
					bapiClass.addImport(List.class);
					field = bapiClass.addField("List<" + structureClassName + "> " + paramName + ";");
					break;
				default:
					throw new HibersapException("Parameter type not expected: " + paramType);
				}
			}

			field.addAnnotation(annotationClass);

			final Annotation<JavaClass> paramterAnno = field.addAnnotation(Parameter.class);
			paramterAnno.setStringValue(param.getSapName());

			if (paramType != ParamType.TABLE && associatedType == null) {
				paramterAnno.setEnumValue("type", ParameterType.STRUCTURE);
			}

			field.setPrivate();
			if (annotationClass == Import.class) {
				field.setFinal(true);
			}
			//will be done by lombok
//			Refactory.createGetterAndSetter(bapiClass, field);
		}
	}

	/**
	 * Creates a simple field at the given class
	 * 
	 * @param name - the field name
	 * @param type - the field type
	 * @param javaClass - the class
	 * @return the new field
	 */
	private Field<JavaClass> createSimpleField(final String name, final String type, final JavaClass javaClass) {
		final Field<JavaClass> field = javaClass.addField();

		field.setName(name);
		field.setType(type);

		return field;
	}

	/**
	 * Creates a structure class for a given parameter mapping with the given Java package
	 * 
	 * @param javaPackage - the Java package
	 * @param parameterMapping - the parameter mapping 
	 * @return the created Java class
	 */
	private JavaClass createStructureClass(final String javaPackage, final ParameterMapping parameterMapping) {
		final String className = SAPEntityBuilder.convertFieldNameToClassName(parameterMapping.getJavaName());
		final JavaClass structureClass = createJavaClass(className, javaPackage);
		final Set<FieldMapping> fieldMappings;

		structureClass.addAnnotation(BapiStructure.class);
		structureClass.addAnnotation(ToString.class);
		structureClass.addAnnotation(Data.class);
		structureClass.addInterface(Serializable.class);


		
		switch (parameterMapping.getParamType()) {
		case STRUCTURE:
			final StructureMapping structureMapping = (StructureMapping) parameterMapping;
			fieldMappings = structureMapping.getParameters();
			break;
		case TABLE:
			final TableMapping tableMapping = (TableMapping) parameterMapping;
			fieldMappings = tableMapping.getComponentParameter().getParameters();
			break;
		default:
			throw new HibersapException("Parameter type not expected: " + parameterMapping.getParamType());
		}

		for (final FieldMapping fieldMapping : fieldMappings) {
			final Field<JavaClass> field = structureClass.addField();
			field.setName(fieldMapping.getJavaName());
			field.setType(fieldMapping.getAssociatedType());
			final Annotation<JavaClass> annotation = field.addAnnotation(Parameter.class);
			annotation.setStringValue(fieldMapping.getSapName());

			//done by lombok
//			Refactory.createGetterAndSetter(structureClass, field);
		}

		//done by lombok
//		Refactory.createToStringFromFields(structureClass);

		return structureClass;
	}

	/**
	 * Converts a field name to a class name
	 * 
	 * @param fieldName - the field name
	 * @return the class name
	 */
	private static String convertFieldNameToClassName(final String fieldName) {
		if (fieldName.length() > 2) {
			final String newFieldName = fieldName.substring(1, 2).toUpperCase() + fieldName.substring(2);

			return newFieldName;
		}

		return fieldName;
	}

	/**
	 * Gets the SAPEntity
	 * 
	 * @return the SAPEntity
	 */
	public SAPEntity getSAPEntity() {
		return this.entity;
	}

}
