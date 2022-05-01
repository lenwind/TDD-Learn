package pers.lenwind.container;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pers.lenwind.container.exception.IllegalInjectionException;
import pers.lenwind.container.exception.MultiInjectException;
import pers.lenwind.container.exception.NoAvailableConstructionException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Nested
class InjectionTest {
    private Dependency dependency = new Dependency() {
    };

    private Context context;

    @BeforeEach
    void setUp() {
        context = mock(Context.class);
        when(context.get(Dependency.class)).thenReturn(Optional.of(dependency));
    }

    @Nested
    class ConstructionInjection {
        @Test
        void should_inject_instance_by_default_construction() {
            Component component = new ComponentProvider<>(Instance.class).get(context);
            assertNotNull(component);
        }

        @Test
        void should_inject_instance_by_tag_annotation_construction_with_dependency() {
            InstanceWithInject bean = new ComponentProvider<>(InstanceWithInject.class).get(context);
            assertSame(dependency, bean.dependency);
        }

        @Test
        void should_throw_exception_if_multi_inject_annotation() {
            MultiInjectException exception = assertThrows(
                MultiInjectException.class,
                () -> new ComponentProvider<>(MultiInjectConstruction.class));
            assertEquals(MultiInjectConstruction.class, exception.getInstanceType());
        }

        @Test
        void should_throw_exception_if_no_inject_annotation_nor_default_construction() {
            NoAvailableConstructionException exception = assertThrows(
                NoAvailableConstructionException.class,
                () -> new ComponentProvider<>(NoAvailableConstruction.class));
            assertEquals(NoAvailableConstruction.class, exception.getInstanceType());
        }
    }

    @Nested
    class FieldInjection {
        @Test
        void should_inject_field_dependencies() {
            ComponentWithFieldDependency component = new ComponentProvider<>(ComponentWithFieldDependency.class).get(context);
            assertSame(dependency, component.dependency);
        }

        @Test
        void should_inject_super_class_field_dependencies() {
            ComponentWithSuperFieldDependency component = new ComponentProvider<>(ComponentWithSuperFieldDependency.class).get(context);
            assertSame(dependency, component.dependency);
        }

        static class ComponentWithFinalField implements Component {
            @Inject
            final Dependency dependency = null;
        }

        @Test
        void should_throw_exception_if_inject_final_field() {
            IllegalInjectionException exception = assertThrows(IllegalInjectionException.class, () -> new ComponentProvider<>(FieldInjection.ComponentWithFinalField.class));
            assertEquals(CommonUtils.getErrorMsg("inject.field.final"), exception.getMsg());
        }
    }

    @Nested
    class MethodInjection {
        @Test
        void should_invoke_methods_if_method_tag_annotation_with_dependency() {
            ComponentWithMethodInject component =
                new ComponentProvider<>(ComponentWithMethodInject.class).get(context);
            assertSame(dependency, component.dependency);
        }

        static class SuperComponentWithMethodInject {
            protected int superCount;

            @Inject
            void setSuperCount() {
                superCount++;
            }
        }

        static class SubComponent extends SuperComponentWithMethodInject {
        }


        static class SubComponentWithMethodInject extends SuperComponentWithMethodInject {
            int subCount;

            @Inject
            public void setSubCount() {
                this.subCount = superCount + 1;
            }
        }

        @Test
        void should_invoke_super_methods_if_tag_annotation() {
            SubComponent component = new ComponentProvider<>(SubComponent.class).get(context);
            assertEquals(1, component.superCount);
        }

        @Test
        void should_invoke_methods_if_tag_annotation_in_super_to_sub_class_order() {
            SubComponentWithMethodInject component = new ComponentProvider<>(SubComponentWithMethodInject.class).get(context);

            assertEquals(1, component.superCount);
            assertEquals(2, component.subCount);
        }



        static class TypeParameterComponent {

            @Inject
            <T> void inject() {
            }
        }

        @Test
        void should_throw_exception_if_exist_type_parameters() {
            assertThrows(IllegalInjectionException.class, () -> new ComponentProvider<>(TypeParameterComponent.class));
        }



        @Nested
        class OverrideMethodInjection {
            static class OverrideMethodInject extends SuperComponentWithMethodInject {
                @Override
                @Inject
                void setSuperCount() {
                    superCount = superCount + 2;
                }
            }

            @Test
            void should_only_invoke_subclass_override_methods_if_both_super_class_and_subclass_tag_annotation() {
                OverrideMethodInject component = new ComponentProvider<>(OverrideMethodInject.class).get(context);

                assertEquals(2, component.superCount);
            }

            static class NoInjectComponent extends SuperComponentWithMethodInject {
                @Override
                void setSuperCount() {
                    super.setSuperCount();
                }
            }

            @Test
            void should_not_invoke_override_methods_if_super_class_tag_annotation_but_sub_not_tag() {
                NoInjectComponent component = new ComponentProvider<>(NoInjectComponent.class).get(context);

                assertEquals(0, component.superCount);
            }

        }
    }
}

class Instance implements Component {
    public Instance() {
    }
}

class InstanceWithInject implements Component {
    Dependency dependency;

    @Inject
    public InstanceWithInject(Dependency dependency) {
        this.dependency = dependency;
    }
}

class MultiInjectConstruction implements Component {

    String obj;

    @Inject
    public MultiInjectConstruction() {
    }

    @Inject
    public MultiInjectConstruction(String obj) {
        this.obj = obj;
    }
}


class NoAvailableConstruction implements Component {
    private NoAvailableConstruction(String noDefault) {
    }
}

class ComponentWithFieldDependency implements Component {
    @Inject
    Dependency dependency;
}

class ComponentWithSuperFieldDependency extends ComponentWithFieldDependency {
}

class ComponentWithMethodInject implements Component {
    Dependency dependency;

    @Inject
    public void setDependency(Dependency dependency) {
        this.dependency = dependency;
    }
}