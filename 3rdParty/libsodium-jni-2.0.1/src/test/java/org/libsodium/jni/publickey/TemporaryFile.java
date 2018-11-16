package org.libsodium.jni.publickey;

import java.io.File;

public class TemporaryFile {
	
	/**
		Cross-platform way to obtain a temporary directory.
		
		Under standard Java, this returns null.
		Under Android while testing, this returns android.support.test.InstrumentationRegistry.getTargetContext().getCacheDir(). This is necessary, because the default implementation of java.io.File.createTempFile() under Android API level 10 is actually defunct.
	*/
	public static File temporaryFileDirectory() {
		return temporaryFileDirectory;
	}
	
	static File temporaryFileDirectory; 
	
	static {
		try {
			Class  instrumentationRegistryClass  = Class.forName("android.support.test.InstrumentationRegistry");           // Should throw ClassNotFoundException on non-Android platforms
			Object targetContext                 = instrumentationRegistryClass.getMethod("getTargetContext").invoke(null);
			TemporaryFile.temporaryFileDirectory = (File) (targetContext.getClass().getMethod("getCacheDir").invoke(targetContext)); 
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		} catch (IllegalAccessException e) {
			throw new ExceptionInInitializerError(e);
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new ExceptionInInitializerError(e);
		} catch (ClassNotFoundException e) {
			// No handling, as this ClassNotFoundException is expected on non-Android platforms.
		}
	}
}

