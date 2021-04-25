
package org.abalazsik.jpa.to.alloy.plugin;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

/**
 *
 * @author ador
 */
public class AlloyWriter {
	
	public static void printSig(PrintWriter writer, String name, String parent, boolean isAbstract, List<Field> variables) {
		StringJoiner joiner = new StringJoiner(",\n");

		for (Field variable : variables) {
			
			String typeName;
			
			try {
				variable.getType().asSubclass(Collection.class);
				typeName = variable.getGenericType().getTypeName();
				
				joiner.add(variable.getName() + " : set " + genericTypeParameterToSimple(typeName));
			} catch(ClassCastException e) {
				typeName = variable.getType().getSimpleName();
				
				joiner.add(variable.getName() + " : " + fqnToSimpleName(typeName));
			}
		}

		writer.append("\n");
		
		if (isAbstract) {
			writer.append("abstract ");
		}
		
		writer.append("sig ").append(name);
		
		if (parent != null) {
			writer.append(" extends ").append(parent);
		}
		
		writer
				.append(" { ")
				.append(joiner.toString())
				.append(" }\n");
	}
	
	public static void printLineComment(PrintWriter writer, String line) {
		writer.append("\n\\\\").append(line).append("\n");
	}
	
	public static void printOneToOne(PrintWriter writer, String name, String type1, String type1Prop, String type2, String type2Prop) {
		printFactHeader(writer, name)
			.append("\t").append("all ").append("m : ").append(type1).append(" | ").append("one n : ").append(type2).append(" | ")
			.append("\n\tm = n.").append(type2Prop).append(" and ").append("m.").append(type1Prop).append(" = ").append("n")
			.append("\n}");
	}
	
	public static void printOneToOne(PrintWriter writer, String type1, String type1Prop, String type2, String type2Prop) {
		printOneToOne(writer, type1 + " to " + type2, type1, type1Prop, type2, type2Prop);
	}
	
	public static void printNotNull(PrintWriter writer, String name, String type1, String type1Prop) {
		printFactHeader(writer, name)
			.append("\tall m : ").append(type1).append(" | some m.").append(type1Prop)
			.append("\n}");
	}
	
	public static void printNotNull(PrintWriter writer, String type1, String type1Prop) {
		printNotNull(writer, type1 + "." + type1Prop + " not null", type1, type1Prop);
	}
	
	public static void printManyToOne(PrintWriter writer, String name, String type1, String type1Prop, String type2, String type2Prop) {
		printFactHeader(writer, name)
				.append("\tall m : ").append(type1).append(" | one n : ").append(type2).append(" | m in n.").append(type2Prop)
				.append("\n}");
	}
	
	public static void printManyToOne(PrintWriter writer, String type1, String type1Prop, String type2, String type2Prop) {
		printManyToOne(writer, "many " + type1 + " to one " + type2 , type1, type1Prop, type2, type2Prop);
	}
	
	public static void printJoin(PrintWriter writer, String name, String child, String childsProp, String parent, String parentsProp) {
		printFactHeader(writer, name)
				.append("\tall m : ").append(child).append(" | ").append("m in m.").append(childsProp).append(".").append(parentsProp)
				.append("\n}\n");
	}
	
	public static void printJoin(PrintWriter writer, String child, String childsProp, String parent, String parentsProp){
		printJoin(writer, "join " + child + "." + childsProp + " to " + parent + "." + parentsProp,
				child, childsProp, parent, parentsProp);
	}
	
	private static PrintWriter printFactHeader(PrintWriter writer, String name) {
		return writer.append("\nfact ").append("\"").append(name).append("\"").append(" {\n");
	}
	
	private static String fqnToSimpleName(String fqn) {
		return fqn.substring(fqn.lastIndexOf(".") + 1, fqn.length());
	}
	
	private static String genericTypeParameterToSimple(String name) {
		return fqnToSimpleName(name.substring(0, name.lastIndexOf(">")));
	}
	
}
