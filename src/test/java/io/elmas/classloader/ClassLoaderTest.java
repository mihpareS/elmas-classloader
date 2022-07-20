package io.elmas.classloader;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class ClassLoaderTest {

    @Test
    void test_loader() {
        String clazzName = "io.sutsaehpeh.zookeeper.warehouse.request.CreateOrderRequest.class";
        ClassLoader loader = ElmasClassLoader.getInstance(new ArrayList<>(), new ArrayList<>());
        try {
            Class<?> clazz = loader.loadClass(clazzName);
            Object req = clazz.getDeclaredConstructor().newInstance();
            System.out.println(req);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ignored) {

        }

    }
}
