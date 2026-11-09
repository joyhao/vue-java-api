package com.joykit.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 类上，表示整个控制器的所有接口仅允许在开发环境访问。
 * 非开发环境访问时返回 403。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DevOnlyController {

    /**
     * 备注，用于说明该控制器为何仅限开发环境。
     */
    String value() default "";
}
