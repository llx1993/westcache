package com.github.bingoohuang.westcache.utils;

import com.github.bingoohuang.westcache.base.WestCacheable;
import com.github.bingoohuang.westcache.impl.WestCacheOption;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * @author bingoohuang [bingoohuang@gmail.com] Created on 2016/12/22.
 */
@UtilityClass
public class CacheAnnotationUtils {
    public WestCacheOption parseWestCacheOption(Method method) {
        val westCacheable = method.getAnnotation(WestCacheable.class);
        if (westCacheable != null) return new WestCacheOption(westCacheable);

        for (val ann : method.getAnnotations()) {
            val optionAnn = parseWestCacheable(ann);
            if (optionAnn != null) return new WestCacheOption(optionAnn);
        }

        return null;
    }

    private WestCacheable parseWestCacheable(Annotation ann) {
        val annotations = ann.annotationType().getAnnotations();
        for (val annotation : annotations) {
            if (annotation instanceof WestCacheable) {
                return (WestCacheable) annotation;
            }
        }

        for (val annotation : annotations) {
            val option = parseWestCacheable(annotation);
            if (option != null) return (WestCacheable) annotation;
        }


        return null;
    }
}
