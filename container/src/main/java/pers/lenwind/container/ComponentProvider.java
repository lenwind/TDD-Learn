package pers.lenwind.container;

import jakarta.inject.Inject;
import pers.lenwind.container.exception.IllegalInjectionException;
import pers.lenwind.container.exception.MultiInjectException;
import pers.lenwind.container.exception.NoAvailableConstructionException;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ComponentProvider<T> implements ContextConfiguration.Provider<T> {
    final Class<T> componentType;

    private final Constructor<T> constructor;
    private final List<Field> injectFields;
    private final List<Method> injectMethods;

    public ComponentProvider(Class<T> componentType) {
        this.componentType = componentType;
        constructor = ComponentUtils.getConstructor(componentType);
        injectFields = ComponentUtils.getInjectFields(componentType);
        if (injectFields.stream().anyMatch(field -> field.getModifiers() == Modifier.FINAL)) {
            throw new IllegalInjectionException(componentType, CommonUtils.getErrorMsg("field.inject.final"));
        }
        injectMethods = ComponentUtils.getInjectMethods(componentType);
    }

    @Override
    public T get(Context context) {
        try {
            Object[] parameters = Arrays.stream(constructor.getParameterTypes())
                .map(dependency -> context.get(dependency).get())
                .toArray();
            T instance = constructor.newInstance(parameters);
            for (Field field : injectFields) {
                field.setAccessible(true);
                field.set(instance, context.get(field.getType()).get());
            }
            for (Method method : injectMethods) {
                method.setAccessible(true);
                Object[] dependencies = Arrays.stream(method.getParameterTypes())
                    .map(type -> context.get(type).get()).toArray();
                method.invoke(instance, dependencies);
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Class<?>> getDependencies() {
        return CommonUtils.concatStream(
            Arrays.stream(constructor.getParameterTypes()),
            injectFields.stream().map(Field::getType),
            injectMethods.stream().flatMap(method1 -> Arrays.stream(method1.getParameterTypes()))).toList();
    }


    static class ComponentUtils {
        static <T> Constructor<T> getConstructor(Class<T> implementation) {
            List<Constructor<?>> constructors = Arrays.stream(implementation.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Inject.class)).toList();
            if (constructors.size() > 1) {
                throw new MultiInjectException(implementation);
            }
            if (constructors.isEmpty()) {
                try {
                    return implementation.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    throw new NoAvailableConstructionException(implementation);
                }
            }
            return (Constructor<T>) constructors.get(0);
        }

        static List<Field> getInjectFields(Class<?> componentType) {
            List<Field> fields = new ArrayList<>();
            Class<?> currentClass = componentType;
            while (currentClass != Object.class) {
                fields.addAll(Arrays.stream(currentClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(Inject.class)).toList());
                currentClass = currentClass.getSuperclass();
            }
            return fields;
        }

        static void checkInjectFields(List<Field> injectFields) {

            for (Field f : injectFields) {
                if (f.getModifiers() == Modifier.FINAL) {

                }
            }
        }

        static List<Method> getInjectMethods(Class<?> componentType) {
            Class<?> currentType = componentType;
            List<Method> methods = new ArrayList<>();
            while (currentType != Object.class) {
                methods.addAll(Arrays.stream(currentType.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Inject.class))
                    .filter(method -> methods.stream()
                        .noneMatch(sub -> Arrays.equals(sub.getParameterTypes(), method.getParameterTypes())
                            && sub.getName().equals(method.getName())))
                    .filter(method -> Arrays.stream(componentType.getDeclaredMethods())
                        .filter(sub -> !sub.isAnnotationPresent(Inject.class))
                        .noneMatch(sub -> Arrays.equals(sub.getParameterTypes(), method.getParameterTypes())
                            && sub.getName().equals(method.getName())))
                    .toList());
                currentType = currentType.getSuperclass();
            }
            Collections.reverse(methods);
            return methods;
        }
    }
}