package com.android.camera.util;

public abstract class SystemProperties {

  private static final Class<?> SP = getSystemPropertiesClass();

  public static String get(String key) {
    try {
      return (String) SP.getMethod("get", String.class).invoke(null, key);
    } catch (Exception e) {
      return null;
    }
  }

  private static Class<?> getSystemPropertiesClass() {
    try {
      return Class.forName("android.os.SystemProperties");
    } catch (ClassNotFoundException shouldNotHappen) {
      return null;
    }
  }
}

