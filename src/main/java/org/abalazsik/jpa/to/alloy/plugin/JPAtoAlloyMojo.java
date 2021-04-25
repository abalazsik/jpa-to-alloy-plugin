package org.abalazsik.jpa.to.alloy.plugin;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.abalazsik.jpa.to.alloy.plugin.ReflectionsUtil.UsableType;

/**
 *
 *
 * @author ador
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES, executionStrategy = "always")
public class JPAtoAlloyMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Parameter(name = "pkg", required = true)
	private String pkg;

	@Parameter(name = "outputFile", required = true)
	private String outputFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {

			File file = new File(project.getBuild().getOutputDirectory());

			URL[] projectClasspath = new URL[]{file.toURI().toURL()};

			URLClassLoader projectClassloader = new URLClassLoader(projectClasspath,
					getClass().getClassLoader());

			File dir = new File(project.getBuild().getOutputDirectory() + "/" + pkg.replace(".", "/"));
			
			File output = new File(
					new File(project.getBuild().getOutputDirectory()).getParent() 
						+ "/" + (outputFile.endsWith(".als") ? outputFile : outputFile + ".als")
			);

			PrintWriter writer = new PrintWriter(output);

			process(dir, pkg, projectClassloader, writer);

			writer.close();

		} catch (Exception ex) {
			this.getLog().error(null, ex);
		}
	}

	private void process(File folder, String pkg, URLClassLoader classLoader, PrintWriter writer) throws ClassNotFoundException {
		String[] files = folder.list((dir, name) -> {
			return name.endsWith(".class");
		});

		Map<String, Class> processableClasses = new ConcurrentHashMap<>(files.length);

		for (String file : files) {
			String name = file.substring(0, file.length() - ".class".length());
			String fqnName = pkg + "." + name;
			Class clazz = classLoader.loadClass(fqnName);
			if (clazz.isAnnotationPresent(Entity.class) 
					|| clazz.isAnnotationPresent(MappedSuperclass.class) 
					|| clazz.isAnnotationPresent(Embeddable.class)) {
				
				processableClasses.put(clazz.getSimpleName(), clazz);
			}
		}
		
		for (Map.Entry<String, Class> entry : processableClasses.entrySet()) {
			processClazz(entry.getValue(), writer, processableClasses);
		}
	}

	private void processClazz(Class clazz, PrintWriter writer, Map<String, Class> processableClasses) {
		
		//PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(clazz);
		ArrayList<Field> modelData = new ArrayList<>(clazz.getDeclaredFields().length);

		for (Field variable : clazz.getDeclaredFields()) {

			if (variable.isAnnotationPresent(JoinColumn.class)
					|| variable.isAnnotationPresent(OneToOne.class)
					|| variable.isAnnotationPresent(ManyToOne.class)
					|| variable.isAnnotationPresent(OneToMany.class)
					|| variable.isAnnotationPresent(ManyToMany.class)
					|| (variable.isAnnotationPresent(Column.class) && variable.getAnnotation(Column.class).nullable() == false)
					|| variable.isAnnotationPresent(Embedded.class)) {

				ReflectionsUtil.UsableType usableType = ReflectionsUtil.getUsableType(variable);

				String genericParamName = usableType.clazz.getSimpleName();

				if (!processableClasses.containsKey(genericParamName)) {
					processableClasses.put(genericParamName, usableType.clazz);
				}

				modelData.add(variable);
			}
		}

		AlloyWriter.printSig(writer, clazz.getSimpleName(),
				getParentName(clazz, processableClasses),
				clazz.isAnnotationPresent(MappedSuperclass.class), modelData);

		for (Field field : clazz.getDeclaredFields()) {
			if (field.isAnnotationPresent(JoinColumn.class)) {
				if (field.getAnnotation(JoinColumn.class).nullable() == false) {
					AlloyWriter.printNotNull(writer, clazz.getSimpleName(), field.getName());
					UsableType usableType = ReflectionsUtil.getUsableType(field);
					Field foreignField = searchRelation(usableType.clazz, field.getName());

					if (foreignField == null) {
						this.getLog().warn("The pair field of " + clazz.getCanonicalName() + "." + field.getName() + " is probably missing!");
					} else {
						AlloyWriter.printJoin(writer, clazz.getSimpleName(), field.getName(), usableType.clazz.getSimpleName(), foreignField.getName());
					}

				}
			}

			if (field.isAnnotationPresent(OneToOne.class)) {
				UsableType usableType = ReflectionsUtil.getUsableType(field);
				Field foreignField = searchRelation(usableType.clazz, field.getName());

				if (foreignField == null) {
					this.getLog().warn("The pair field of " + clazz.getCanonicalName() + "." + field.getName() + " is probably missing!");
				} else {
					AlloyWriter.printOneToOne(writer, clazz.getSimpleName(), field.getName(), usableType.clazz.getSimpleName(), foreignField.getName());
				}
			}

			if (field.isAnnotationPresent(ManyToOne.class)) {

				if (field.isAnnotationPresent(JoinColumn.class)) {
					UsableType usableType = ReflectionsUtil.getUsableType(field);
					Field foreignField = searchRelation(usableType.clazz, field.getName());

					AlloyWriter.printManyToOne(writer, clazz.getSimpleName(), field.getName(), usableType.clazz.getSimpleName(), foreignField.getName());
				} else {
					this.getLog().warn("The @JoinColumn is not present on " + clazz.getCanonicalName() + "." + field.getName() + "!");
				}
			}
		}
	}
	
	private String getParentName(Class clazz, Map<String, Class> processedClasses) {
		if (clazz.getSuperclass() == null) {
			return null;
		}
		
		Class superClass = clazz.getSuperclass();
		
		if (processedClasses.containsKey(superClass.getSimpleName())) {
			return superClass.getSimpleName();
		} else {
			return null;
		}
	}
	
	private Field searchRelation(Class clazz, String mappedBy) {

		for (Field field : clazz.getDeclaredFields()) {
			UsableType usableType = ReflectionsUtil.getUsableType(field);
			
			if (field.isAnnotationPresent(OneToOne.class) && mappedBy.equals(field.getAnnotation(OneToOne.class).mappedBy())) {
				return field;
			} else if (field.isAnnotationPresent(OneToMany.class) && mappedBy.equals(field.getAnnotation(OneToMany.class).mappedBy())) {
				return field;
			}
		}

		return null;
	}

}
