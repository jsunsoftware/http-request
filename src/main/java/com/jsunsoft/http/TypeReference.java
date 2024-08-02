/*
 * Copyright 2017 Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jsunsoft.http;

import java.lang.reflect.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * This generic abstract class is used for obtaining full generics type information
 * Class is based on ideas from
 * <a href="http://gafter.blogspot.com/2006/12/super-type-tokens.html"
 * >http://gafter.blogspot.com/2006/12/super-type-tokens.html</a>,
 * Additional idea (from a suggestion made in comments of the article)
 * is to require bogus implementation of <code>Comparable</code>
 * (any such generic interface would do, as long as it forces a method
 * with generic type to be implemented).
 * to ensure that a Type argument is indeed given.
 * <p>
 * Usage is by sub-classing: here is one way to instantiate reference
 * to generic type <code>List&lt;Integer&gt;</code>:
 * <pre>
 *  TypeReference ref = new TypeReference&lt;List&lt;Integer&gt;&gt;() { };
 *  </pre>
 */
@SuppressWarnings("rawtypes")
public class TypeReference<T> implements Comparable<TypeReference<T>> {

    /**
     * Type represented by the generic type instance.
     */
    private final Type type;
    /**
     * The actual raw parameter type.
     */
    private final Class<?> rawType;

    /**
     * Constructs a new generic type, deriving the generic type and class from
     * type parameter. Note that this constructor is protected, users should create
     * a (usually anonymous) subclass as shown above.
     *
     * @throws IllegalArgumentException in case the generic type parameter value is not
     *                                  provided by any of the subclasses.
     */
    protected TypeReference() {
        // Get the type parameter of TypeReference<T> (aka the T value)
        type = getTypeArgument(getClass(), TypeReference.class);
        rawType = getClass(type);
    }

    /**
     * Constructs a new generic type, supplying the generic type
     * information and deriving the class.
     *
     * @param typeReference the generic type.
     *
     * @throws IllegalArgumentException if typeReference is {@code null} or not an instance of
     *                                  {@code Class} or {@link ParameterizedType} whose raw
     *                                  type is an instance of {@code Class}.
     */
    public TypeReference(Type typeReference) {
        if (typeReference == null) {
            throw new IllegalArgumentException("Type must not be null");
        }

        type = typeReference;
        rawType = getClass(type);
    }

    TypeReference(Type type, Class<?> rawType) {
        this.type = ArgsCheck.notNull(type, "type");
        this.rawType = ArgsCheck.notNull(rawType, "rawType");
    }

    /**
     * Retrieve the type represented by the generic type instance.
     *
     * @return the actual type represented by this generic type instance.
     */
    public final Type getType() {
        return type;
    }

    /**
     * Returns the object representing the class or interface that declared
     * the type represented by this generic type instance.
     *
     * @return the class or interface that declared the type represented by this
     * generic type instance.
     */
    public final Class<?> getRawType() {
        return rawType;
    }

    /**
     * Returns the object representing the class or interface that declared
     * the supplied {@code type}.
     *
     * @param type {@code Type} to inspect.
     *
     * @return the class or interface that declared the supplied {@code type}.
     */
    private static Class getClass(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
                return (Class) parameterizedType.getRawType();
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            final Class<?> componentRawType = getClass(array.getGenericComponentType());
            return getArrayClass(componentRawType);
        }
        throw new IllegalArgumentException("Type parameter " + type.toString() + " not a class or " +
                "parameterized type whose raw type is a class");
    }

    /**
     * Get Array class of component class.
     *
     * @param c the component class of the array
     *
     * @return the array class.
     */
    private static Class getArrayClass(Class c) {
        try {
            Object o = Array.newInstance(c, 0);
            return o.getClass();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Return the value of the type parameter of {@code TypeReference<T>}.
     *
     * @param clazz     subClass of {@code baseClass} to analyze.
     * @param baseClass base class having the type parameter the value of which we need to retrieve
     *
     * @return the parameterized type of {@code TypeReference<T>} (aka T)
     */
    static Type getTypeArgument(Class<?> clazz, Class<?> baseClass) {
        // collect superclasses
        Deque<Type> superclasses = new ArrayDeque<Type>();
        Type currentType;
        Class<?> currentClass = clazz;
        do {
            currentType = currentClass.getGenericSuperclass();
            superclasses.push(currentType);
            if (currentType instanceof Class) {
                currentClass = (Class) currentType;
            } else if (currentType instanceof ParameterizedType) {
                currentClass = (Class) ((ParameterizedType) currentType).getRawType();
            }
        } while (!currentClass.equals(baseClass));

        // find which one supplies type argument and return it
        TypeVariable tv = baseClass.getTypeParameters()[0];
        while (!superclasses.isEmpty()) {
            currentType = superclasses.pop();

            if (currentType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) currentType;
                Class<?> rawType = (Class) pt.getRawType();
                int argIndex = Arrays.asList(rawType.getTypeParameters()).indexOf(tv);
                if (argIndex > -1) {
                    Type typeArg = pt.getActualTypeArguments()[argIndex];
                    if (typeArg instanceof TypeVariable) {
                        // type argument is another type variable - look for the value of that
                        // variable in subclasses
                        tv = (TypeVariable) typeArg;
                        continue;
                    } else {
                        // found the value - return it
                        return typeArg;
                    }
                }
            }

            // needed type argument not supplied - break and throw exception
            break;
        }
        throw new IllegalArgumentException(currentType + " does not specify the type parameter T of TypeReference<T>");
    }

    @Override
    public boolean equals(Object obj) {
        boolean result = this == obj;
        if (!result && obj instanceof TypeReference) {
            // Compare inner type for equality
            TypeReference<?> that = (TypeReference<?>) obj;
            return this.type.equals(that.type);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    /**
     * The only reason we define this method (and require implementation
     * of <code>Comparable</code>) is to prevent constructing a
     * reference without type information.
     */
    @Override
    public int compareTo(TypeReference<T> o) {
        return 0;
    }
    // just need an implementation, not a good one... hence ^^^

    @Override
    public String toString() {
        return "TypeReference{" + type.toString() + "}";
    }
}

