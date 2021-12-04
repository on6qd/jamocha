package cc.javastudio.jamocha;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.management.RuntimeErrorException;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.SearchConfig;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;


/**
 * Injector, to create objects for all @CustomService classes. autowire/inject
 * all dependencies
 */
public class Injector {
    private final Map<Class<?>, Class<?>> diMap;
    private final Map<Class<?>, Object> applicationScope;

    private static Injector injector;

    private Injector() {
        super();
        diMap = new HashMap<>();
        applicationScope = new HashMap<>();
    }

    /**
     * Start application
     *
     * @param mainClass - class containing the main method
     */
    public static void startApplication(Class<?> mainClass) {
        try {
            synchronized (Injector.class) {
                if (injector == null) {
                    injector = new Injector();
                    injector.initFramework(mainClass);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static <T> T getBean(Class<T> aClass) {
        try {
            return injector.getBeanInstance(aClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * initialize the injector framework
     */
    private void initFramework(Class<?> mainClass)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String packageRelPath = mainClass.getPackage().getName().replace(".", "/");
        SearchConfig searchConfig = SearchConfig
                .forResources(packageRelPath)
                .by(ClassCriteria.create().allThoseThatMatch(
                        clazz -> clazz.getAnnotation(Component.class) != null
                ));

        ComponentContainer componentContainer = ComponentContainer.getInstance();
        ComponentContainer.create("org/burningwave/custom-config-file.properties");
        ClassHunter classHunter = componentContainer.getClassHunter();
        try (ClassHunter.SearchResult result = classHunter.findBy(searchConfig)) {
            Collection<Class<?>> classes = result.getClasses();
            for (Class<?> implementationClass : classes) {
                Class<?>[] interfaces = implementationClass.getInterfaces();
                if (interfaces.length == 0) {
                    diMap.put(implementationClass, implementationClass);
                } else {
                    for (Class<?> anInterface : interfaces) {
                        diMap.put(implementationClass, anInterface);
                    }
                }
            }
            for (Class<?> aClass : classes) {
                if (aClass.isAnnotationPresent(Component.class)) {
                    Object newBean = autowire(aClass);
                    applicationScope.put(aClass,newBean);
                }

            }
        }
    }

    /**
     * Perform injection recursively, for each service inside the Client class
     */
    public Object autowire(Class<?> classz)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        ConstructorCriteria constructorCriteria = ConstructorCriteria
                .withoutConsideringParentClasses()
                .allThoseThatMatch(constructor -> constructor.getParameterCount() > 0);

        var constructors = Constructors.findAllAndMakeThemAccessible(constructorCriteria, classz);

        Object newBean = null;
        if (constructors.size() > 0) {
            for (var constructor : constructors) {
                List<Object> dependencies = new ArrayList<>();
                for (var parameter : constructor.getParameterTypes()) {
                    Object anInstance = getBeanInstance(parameter, parameter.getName(), null);
                    if (anInstance != null) {
                        dependencies.add(anInstance);
                    }
                }
                newBean = constructor.newInstance(dependencies.toArray());
            }
        } else {
            newBean = classz.getDeclaredConstructor().newInstance();
        }
        return newBean;
    }

    /**
     * Create and Get the Object instance of the implementation class for input
     * interface service
     */
    private <T> T getBeanInstance(Class<T> interfaceClass) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return (T) getBeanInstance(interfaceClass, null, null);
    }

    /**
     * Overload getBeanInstance to handle qualifier and autowire by type
     */
    public <T> Object getBeanInstance(Class<T> interfaceClass, String fieldName, String qualifier)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class<?> implementationClass = getImplementationClass(interfaceClass, fieldName, qualifier);

        if (applicationScope.containsKey(implementationClass)) {
            return applicationScope.get(implementationClass);
        }

        synchronized (applicationScope) {
            Object service = implementationClass.getDeclaredConstructor().newInstance();
            applicationScope.put(implementationClass, service);
            return service;
        }
    }

    /**
     * Get the name of the implementation class for input interface service
     */
    private Class<?> getImplementationClass(Class<?> interfaceClass, final String fieldName, final String qualifier) {
        Set<Entry<Class<?>, Class<?>>> implementationClasses = diMap.entrySet().stream()
                .filter(entry -> entry.getValue() == interfaceClass).collect(Collectors.toSet());
        String errorMessage = "";
        if (implementationClasses.size() == 0) {
            errorMessage = "no implementation found for interface " + interfaceClass.getName();
        } else if (implementationClasses.size() == 1) {
            Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream().findFirst();
            return optional.get().getKey();
        } else {
            final String findBy = (qualifier == null || qualifier.trim().length() == 0) ? fieldName : qualifier;
            Optional<Entry<Class<?>, Class<?>>> optional = implementationClasses.stream()
                    .filter(entry -> entry.getKey().getSimpleName().equalsIgnoreCase(findBy)).findAny();
            if (optional.isPresent()) {
                return optional.get().getKey();
            } else {
                errorMessage = "There are " + implementationClasses.size() + " of interface " + interfaceClass.getName()
                        + " Expected single implementation or make use of @CustomQualifier to resolve conflict";
            }
        }
        throw new RuntimeErrorException(new Error(errorMessage));
    }
}