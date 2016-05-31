package com.slickqa.executioner.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a component that should be initialized
 * on startup.  Executioner will locate it, use Guice to create an instance, and
 * add it to the appropriate list depending on what interfaces it implements.
 */
@Target(ElementType.TYPE)
public @interface AutoloadComponent {
}
