package cc.javastudio.jamocha;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;
import javax.management.RuntimeErrorException;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import static org.reflections.ReflectionUtils.*;
import static org.reflections.scanners.Scanners.MethodsParameter;
import static org.reflections.scanners.Scanners.TypesAnnotated;

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
        var startPackage = mainClass.getPackage().getName();
        var reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackage(startPackage)
                        .setScanners(TypesAnnotated, MethodsParameter));
        var classes = reflections.get(TypesAnnotated.with(Component.class).asClass());
        for (var implementationClass : classes) {
            var interfaces = implementationClass.getInterfaces();
            if (interfaces.length == 0) {
                diMap.put(implementationClass, implementationClass);
            } else {
                for (Class<?> anInterface : interfaces) {
                    diMap.put(implementationClass, anInterface);
                }
            }
        }
        for (var aClass : classes) {
            if (aClass.isAnnotationPresent(Component.class)) {
                Object newBean = autowire(aClass);
                applicationScope.put(aClass, newBean);
            }
        }
    }

    /**
     * Perform injection recursively, for each service inside the Client class
     */
    public Object autowire(Class<?> aClass)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        var constructors = get(Constructors.of(aClass));
        var constructor = constructors.stream().findFirst().get();
        if (constructor.getParameterCount() > 0) {
            var dependencies = new ArrayList<>();
            var annotations = constructor.getParameterAnnotations();
            var parameters = constructor.getParameterTypes();
            for (int i = 0; i< parameters.length; i++) {
                var parameter = parameters[i];
                var qualifier = hasQualifier(annotations[i]);
                Object anInstance = getBeanInstance(parameter, parameter.getName(), qualifier);
                dependencies.add(anInstance);
            }
            return constructor.newInstance(dependencies.toArray());
        } else {
            return aClass.getDeclaredConstructor().newInstance();
        }
    }

    private static String hasQualifier(Annotation[] annotations) {
        for (var annotation : annotations) {
            if(Qualifier.class.isInstance(annotation)) {
                var argumentAnnotation = (Qualifier) annotation;
                return argumentAnnotation.value();
            }
        }
        return null;
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
        var implementationClass = getImplementationClass(interfaceClass, fieldName, qualifier);

        if (applicationScope.containsKey(implementationClass)) {
            return applicationScope.get(implementationClass);
        }
        synchronized (applicationScope) {
            Object newBean = autowire(implementationClass);
            applicationScope.put(implementationClass, newBean);
            return newBean;
        }
    }

    /**
     * Get the name of the implementation class for input interface service
     */
    private Class<?> getImplementationClass(Class<?> interfaceClass, final String fieldName, final String qualifier) {
        var implementationClasses = diMap.entrySet().stream()
                .filter(entry -> entry.getValue() == interfaceClass).collect(Collectors.toSet());
        var errorMessage = "";
        if (implementationClasses.size() == 0) {
            errorMessage = "no implementation found for interface " + interfaceClass.getName();
        } else if (implementationClasses.size() == 1) {
            var optional = implementationClasses.stream().findFirst();
            return optional.get().getKey();
        } else {
            final String findBy = (qualifier == null || qualifier.trim().length() == 0) ? fieldName : qualifier;
            var optional = implementationClasses.stream()
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