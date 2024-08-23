package com.github.PizzaBoytjuh.ReplaceUtils.replacer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.github.PizzaBoytjuh.ReplaceUtils.replacer.annotation.OriginalMethod;
import com.github.PizzaBoytjuh.ReplaceUtils.replacer.exception.InterfaceException;
import com.github.PizzaBoytjuh.ReplaceUtils.replacer.exception.UndefinedMethodException;

public class Replacer<T extends Replaceable> {
	
	//TODO make a better name
	private HashMap<Method, MethodTuple> methodCalls = new HashMap<>();
	
	private Set<Method> undefinedMethods = new HashSet<>();
	
	//TODO make this Set immutable
	private Set<Method> methods = new HashSet<>();
	
	public final T proxy;
	public final Class<T> clazz;
	
	@SuppressWarnings("unchecked")
	public Replacer(Class<T> replaceable) {
		this.clazz = replaceable;
		
		// The replaceable has to be an interface due to the nature of Proxies
		if(replaceable == null || !replaceable.isInterface()) throw new InterfaceException("Tried making a Replacer with type other than an interface");
		
		for(Method m : replaceable.getMethods()) {
			methods.add(m);
			
			// Try finding the original static method using the Original annotation. If it is not there the method is undefined
			OriginalMethod org = m.getAnnotation(OriginalMethod.class);
			if(org == null) {
				undefinedMethods.add(m);
				continue;
			}
			
			Method original = null;
			try {
				original = org.owner().getMethod(org.method(), m.getParameterTypes());
			} catch(NoSuchMethodException nsme) {
				undefinedMethods.add(m);
			}
			
			methodCalls.put(m, new MethodTuple(original, null));
		}
		
		// Make the proxy instance to reroute method invokes
		proxy = (T) Proxy.newProxyInstance(replaceable.getClassLoader(), new Class<?>[] { replaceable }, (proxy, method, args) -> {
			if(method.equals(replaceable.getMethod("getReplacer"))) {
				return this;
			}
			
			MethodTuple toCall = methodCalls.get(method);
			if(toCall == null) throw new UndefinedMethodException(method.getName() + " is not defined!");
			
			try {
				return toCall.invoke(args);
			} catch(InvocationTargetException e) {
				throw e.getTargetException();
			}
		});
	}
	
	public void replaceStatic(Method inInterface, Method replaceWith) throws NoSuchMethodException {
		// Make sure inInterface actually exists
		if(inInterface == null || !methods.contains(inInterface)) throw new NoSuchMethodException("Can't find method " + inInterface == null ? "null" : inInterface.getName() + " in " + clazz.getName());
		
		// If the replaceWith is null, the method in the interface becomes undefined
		if(replaceWith == null) {
			methodCalls.remove(inInterface);
			undefinedMethods.add(inInterface);
			return;
		}
		
		methodCalls.put(inInterface, new MethodTuple(replaceWith, null));
		undefinedMethods.remove(inInterface);
	}
	
	public void replace(Method inInterface, Method replaceWith, Object from) {
		// inInterface cannot be null
		// TODO make a better exception for this case
		if(inInterface == null) throw new NullPointerException();
		
		if(replaceWith == null) {
			methodCalls.remove(inInterface);
			undefinedMethods.add(inInterface);
			return;
		}
		
		methodCalls.put(inInterface, new MethodTuple(replaceWith, from));
		undefinedMethods.remove(inInterface);
	}
	
	//TODO make immutable
	public Set<Method> definedMethods() {
		return methodCalls.keySet();
	}
	
	//TODO make immutable
	public Set<Method> undefinedMethods() {
		return undefinedMethods;
	}
	
	//TODO make immutable
	public Set<Method> Methods() {
		return methods;
	}
	
	public void massReplaceStatic(Class<?> cls) {
		for(Method m : cls.getMethods()) {
			try {
				// Method has to be static in order to be replaced using just the class
				if(!Modifier.isStatic(m.getModifiers())) continue;
				Method interfaceMethod = clazz.getMethod(m.getName(), m.getParameterTypes());
				replaceStatic(interfaceMethod, m);
			} catch (Throwable e) {
				continue;
			}
		}
	}
	
	public void massReplace(Object obj) {
		for(Method m : obj.getClass().getMethods()) {
			try {
				// Method cannot be static so you can choose to replace the methods from an Object (using Replacer#massReplace) or to replace the methods staticly (using Replacer#massReplaceStatic)
				if(Modifier.isStatic(m.getModifiers())) continue;
				Method interfaceMethod = clazz.getMethod(m.getName(), m.getParameterTypes());
				replace(interfaceMethod, m, obj);
			} catch (Throwable e) {
				continue;
			}
		}
	}
	
	//A utility class to easily handle both static and non-static methods
	//TODO make better name
	private class MethodTuple {
		
		public Method method;
		public Object obj;
		
		public MethodTuple(Method method, Object obj) {
			this.method = method;
			this.obj = obj;
		}
		
		public Object invoke(Object... args) throws IllegalAccessException, InvocationTargetException {
			return method.invoke(obj, args);
		}
		
	}
	
}
