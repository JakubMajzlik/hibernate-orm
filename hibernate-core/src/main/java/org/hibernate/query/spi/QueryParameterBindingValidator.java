/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query.spi;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.BindableType;
import org.hibernate.query.sqm.SqmExpressable;
import org.hibernate.type.descriptor.converter.AttributeConverterTypeAdapter;
import org.hibernate.type.descriptor.java.JavaType;

import jakarta.persistence.TemporalType;

/**
 * @author Andrea Boriero
 */
public class QueryParameterBindingValidator {

	public static final QueryParameterBindingValidator INSTANCE = new QueryParameterBindingValidator();

	private QueryParameterBindingValidator() {
	}

	public void validate(BindableType<?> paramType, Object bind, SessionFactoryImplementor sessionFactory) {
		validate( paramType, bind, null, sessionFactory );
	}

	public void validate(
			BindableType<?> paramType,
			Object bind,
			TemporalType temporalPrecision,
			SessionFactoryImplementor sessionFactory) {
		if ( bind == null || paramType == null ) {
			// nothing we can check
			return;
		}

		if ( paramType instanceof AttributeConverterTypeAdapter ) {
			final AttributeConverterTypeAdapter<?> converterTypeAdapter = (AttributeConverterTypeAdapter<?>) paramType;
			final JavaType<?> domainJtd = converterTypeAdapter.getDomainJtd();

			if ( domainJtd.getJavaTypeClass().isInstance( bind ) ) {
				return;
			}
		}

		final Class<?> parameterJavaType;
		if ( paramType.getBindableJavaType() != null ) {
			parameterJavaType = paramType.getBindableJavaType();
		}
		else {
			final SqmExpressable<?> sqmExpressable = paramType.resolveExpressable( sessionFactory );
			parameterJavaType = sqmExpressable.getBindableJavaType();
		}

		if ( parameterJavaType == null ) {
			// nothing we can check
			return;
		}

		if ( bind instanceof Collection && !Collection.class.isAssignableFrom( parameterJavaType ) ) {
			// we have a collection passed in where we are expecting a non-collection.
			// 		NOTE : this can happen in Hibernate's notion of "parameter list" binding
			// 		NOTE2 : the case of a collection value and an expected collection (if that can even happen)
			//			will fall through to the main check.
			validateCollectionValuedParameterBinding( parameterJavaType, (Collection<?>) bind, temporalPrecision );
		}
		else if ( bind.getClass().isArray() ) {
			validateArrayValuedParameterBinding( parameterJavaType, bind, temporalPrecision );
		}
		else {
			if ( !isValidBindValue( parameterJavaType, bind, temporalPrecision ) ) {
				throw new IllegalArgumentException(
						String.format(
								"Parameter value [%s] did not match expected type [%s (%s)]",
								bind,
								parameterJavaType.getName(),
								extractName( temporalPrecision )
						)
				);
			}
		}
	}

	private String extractName(TemporalType temporalType) {
		return temporalType == null ? "n/a" : temporalType.name();
	}

	private void validateCollectionValuedParameterBinding(
			Class<?> parameterType,
			Collection<?> value,
			TemporalType temporalType) {
		// validate the elements...
		for ( Object element : value ) {
			if ( !isValidBindValue( parameterType, element, temporalType ) ) {
				throw new IllegalArgumentException(
						String.format(
								"Parameter value element [%s] did not match expected type [%s (%s)]",
								element,
								parameterType.getName(),
								extractName( temporalType )
						)
				);
			}
		}
	}

	private static boolean isValidBindValue(Class<?> expectedType, Object value, TemporalType temporalType) {
		if ( expectedType.isPrimitive() ) {
			if ( expectedType == boolean.class ) {
				return value instanceof Boolean;
			}
			else if ( expectedType == char.class ) {
				return value instanceof Character;
			}
			else if ( expectedType == byte.class ) {
				return value instanceof Byte;
			}
			else if ( expectedType == short.class ) {
				return value instanceof Short;
			}
			else if ( expectedType == int.class ) {
				return value instanceof Integer;
			}
			else if ( expectedType == long.class ) {
				return value instanceof Long;
			}
			else if ( expectedType == float.class ) {
				return value instanceof Float;
			}
			else if ( expectedType == double.class ) {
				return value instanceof Double;
			}
			return false;
		}
		else if ( value == null) {
			return true;
		}
		else if ( expectedType.isInstance( value ) ) {
			return true;
		}
		else if ( temporalType != null ) {
			final boolean parameterDeclarationIsTemporal = Date.class.isAssignableFrom( expectedType )
					|| Calendar.class.isAssignableFrom( expectedType );
			final boolean bindIsTemporal = value instanceof Date
					|| value instanceof Calendar;

			return parameterDeclarationIsTemporal && bindIsTemporal;
		}

		return false;
	}

	private void validateArrayValuedParameterBinding(
			Class<?> parameterType,
			Object value,
			TemporalType temporalType) {
		if ( !parameterType.isArray() ) {
			throw new IllegalArgumentException(
					String.format(
							"Encountered array-valued parameter binding, but was expecting [%s (%s)]",
							parameterType.getName(),
							extractName( temporalType )
					)
			);
		}

		if ( value.getClass().getComponentType().isPrimitive() ) {
			// we have a primitive array.  we validate that the actual array has the component type (type of elements)
			// we expect based on the component type of the parameter specification
			if ( !parameterType.getComponentType().isAssignableFrom( value.getClass().getComponentType() ) ) {
				throw new IllegalArgumentException(
						String.format(
								"Primitive array-valued parameter bind value type [%s] did not match expected type [%s (%s)]",
								value.getClass().getComponentType().getName(),
								parameterType.getName(),
								extractName( temporalType )
						)
				);
			}
		}
		else {
			// we have an object array.  Here we loop over the array and physically check each element against
			// the type we expect based on the component type of the parameter specification
			final Object[] array = (Object[]) value;
			for ( Object element : array ) {
				if ( !isValidBindValue( parameterType.getComponentType(), element, temporalType ) ) {
					throw new IllegalArgumentException(
							String.format(
									"Array-valued parameter value element [%s] did not match expected type [%s (%s)]",
									element,
									parameterType.getName(),
									extractName( temporalType )
							)
					);
				}
			}
		}
	}
}
