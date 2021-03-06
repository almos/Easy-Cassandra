/*
 * Copyright 2012 Otávio Gonçalves de Santana (otaviojava)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.easycassandra.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The class does getter and setter with invoke Dynamic
 *
 * @author otavio
 */
public final class ReflectionUtil {

    /**
     * class of primitives types
     */
    @SuppressWarnings("rawtypes")
	private static Class[] primitivesClass = {int.class, long.class,
        float.class, double.class, boolean.class};

    /**
     * Return The Object from the Field
     *
     * @param object
     * @param field
     * @return - the field value in Object
     */
    public static Object getMethod(Object object, Field field) {

        try {
        	boolean isAccessibleCopy=field.isAccessible();
          if(isAccessibleCopy){
        	  return field.get(object);
          }
          else{
        	  field.setAccessible(true);
        	  Object value=field.get(object);
        	  field.setAccessible(isAccessibleCopy);
        	  return value;
          }
          
        } catch (Exception exception) {
            Logger.getLogger(ReflectionUtil.class.getName()).log(Level.SEVERE,
                    null, exception);
        }
        return null;
    }

    /**
     * Set the field in the Object
     *
     * @param object
     * @param field
     * @param value
     * @return - if the operation was execute with sucess
     */
    public static boolean setMethod(Object object, Field field, Object value) {
        try {
        	boolean isAccessibleCopy=field.isAccessible();
            if(isAccessibleCopy){
          	field.set(object, value);
            }
            else{
          	  field.setAccessible(true);
          	 field.set(object, value);
          	  field.setAccessible(isAccessibleCopy);
            }
        } catch (Exception exception) {
            Logger.getLogger(ReflectionUtil.class.getName()).log(Level.SEVERE,
                    null, exception);
            return false;
        }
        return true;
    }

    /**
     * method for use the value Of of the Object, for this the class must be the
     * static method value of and return a new instance of itself
     *
     * @param classValue - the reference of the Class
     * @param value -the value
     * @param refence - the Class Reference
     * @return the instance of the object
     */
    @SuppressWarnings("rawtypes")
	public static Object valueOf(Class classValue, Object value, Class refence) {
        try {
            if (Arrays.asList(primitivesClass).contains(classValue)) {
                return valueOfWrapper(classValue, value);
            }
            @SuppressWarnings("unchecked")
			Method method= classValue.getMethod("valueOf", refence);
            return method.invoke(classValue,value);

        
        } catch (Exception exception) {
            Logger.getLogger(ReflectionUtil.class.getName()).log(Level.SEVERE,
                    null, exception);
            return null;
        }


    }

    /**
     * Do whrapper in value of method
     *
     * @param classValue - value of the class
     * @param value - the value of the class
     * @return - the Object for the primitive type
     */
    private static Object valueOfWrapper(Class<?> classValue, Object value) {

            if(int.class.equals(classValue)){
                return Integer.valueOf(value.toString());
            }
            if(long.class.equals(classValue)){
                return Long.valueOf(value.toString());
            } 
            if(float.class.equals(classValue)){
                return Float.valueOf(value.toString());
            }
            if(double.class.equals(classValue)){
                return Double.valueOf(value.toString());
            }
            if(boolean.class.equals(classValue)){
                return Boolean.valueOf(value.toString());
            }

        return value;
    }

    /**
     * @see ReflectionUtil#valueOf(Class, Object, Class)
     * @param classValue
     * @param value
     * @return the value of value
     */
    public static Object valueOf(Class<?> classValue, Object value) {
        return valueOf(classValue, value, value.getClass());
    }

    /**
     * Create new instance of this class
     *
     * @param clazz
     * @return the new instance that class
     */
    public static Object newInstance(Class<?> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception exception) {
            Logger.getLogger(ReflectionUtil.class.getName()).log(Level.SEVERE,
                    null, exception);
            return null;
        }
    }

    /**
     * find the Field from the name field
     *
     * @param string
     * @return the field from the name
     */
    public static Field getField(String string, Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(string)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Singleton
     */
    private ReflectionUtil() {
    }
}
