package org.abalazsik.jpa.to.alloy.plugin;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;

/**
 *
 * @author ador
 */
public class ReflectionsUtil {

	public static UsableType getUsableType(Field field) {
		try {
			field.getType().asSubclass(Collection.class);ParameterizedType t;

			Class genericParam = field.getType().getTypeParameters()[0].getGenericDeclaration();

			return new UsableType(true, genericParam);//TODO: fix this
		} catch (ClassCastException e) {
			return new UsableType(false, field.getType());
		}
	}

	public static class UsableType {

		public final boolean isCollection;
		public final Class clazz;

		public UsableType(boolean isCollection, Class clazz) {
			this.isCollection = isCollection;
			this.clazz = clazz;
		}

	}
}
