package io.elmas.classloader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ElmasClassLoader extends ClassLoader {


    private static final String basePath;

    private static final String WINDOWS_FILE_SEPARATOR = "\\";

    private static final String UNIX_FILE_SEPARATOR = "/";

    private static final String EMPTY = "";

    private static final String CLASS_EXT = ".class";

    private static final Pattern DOT_REGEX_PATTERN = Pattern.compile("\\.");

    private static final ReentrantLock lock = new ReentrantLock();

    private static volatile ElmasClassLoader INSTANCE;

    static {
        basePath = System.getenv("io.elmas");
    }

    private final ConcurrentHashMap<Object, String> classNameMap;

    private final LoaderInternal loaderInternal;

    private final ElmasFileVisitor fileVisitor;

    private static class LoaderInternal {

        private final String replaceTarget;

        private final String parsedBasePath;

        private final List<String> parsedIgnoredPackages;

        private final List<String> parsedIgnoredClasses;

        LoaderInternal(List<String> ignoredPackages, List<String> ignoredClasses) {
            replaceTarget = File.separator.equals(WINDOWS_FILE_SEPARATOR) ?
                    WINDOWS_FILE_SEPARATOR.concat(WINDOWS_FILE_SEPARATOR) :
                    UNIX_FILE_SEPARATOR;

            String windowsFileSeparatorReg = WINDOWS_FILE_SEPARATOR.concat(WINDOWS_FILE_SEPARATOR);

            parsedBasePath = basePath.replaceAll(windowsFileSeparatorReg, replaceTarget)
                    .replaceAll(UNIX_FILE_SEPARATOR, replaceTarget);

            List<String> ignoredPackageCopy = new ArrayList<>(ignoredPackages);
            List<String> ignoredClassCopy = new ArrayList<>(ignoredClasses);

            parsedIgnoredPackages = ignoredPackageCopy.stream().map(
                    pkg -> DOT_REGEX_PATTERN.matcher(pkg).replaceAll(File.separator)
            ).collect(Collectors.toList());

            parsedIgnoredClasses = ignoredClassCopy.stream().map(
                    clz -> {
                        int lastDot = clz.lastIndexOf('.');
                        return DOT_REGEX_PATTERN.matcher(clz.substring(0, lastDot))
                                .replaceAll(File.separator).concat(CLASS_EXT);
                    }).collect(Collectors.toList());

        }
    }

    private class ElmasFileVisitor extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String path = dir.toAbsolutePath().toString();
            for (String pkg : loaderInternal.parsedIgnoredPackages) {
                if (path.contains(pkg)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String path = file.toAbsolutePath().toString();
            if (!path.contains(CLASS_EXT)) {
                return FileVisitResult.CONTINUE;
            }
            for (String clazz : loaderInternal.parsedIgnoredClasses) {
                if (path.contains(clazz)) {
                    return FileVisitResult.CONTINUE;
                }
            }
            int lastDot = path.lastIndexOf('.');
            String withoutExt = path.substring(0, lastDot);
            String classWithoutRootPath = withoutExt.replace(loaderInternal.parsedBasePath.concat(File.separator), EMPTY);
            String classFullName = classWithoutRootPath.replaceAll(loaderInternal.replaceTarget, ".")
                    .concat(CLASS_EXT);
            classNameMap.putIfAbsent(classFullName, path);
            return super.visitFile(file, attrs);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return super.postVisitDirectory(dir, exc);
        }
    }

    private ElmasClassLoader(List<String> ignoredPackages, List<String> ignoredClasses) {
        classNameMap = new ConcurrentHashMap<>();
        loaderInternal = new LoaderInternal(ignoredPackages, ignoredClasses);
        fileVisitor = new ElmasFileVisitor();
        init();
    }

    public static ElmasClassLoader getInstance(List<String> ignoredPackages, List<String> ignoredClasses) {
        if (INSTANCE == null) {
            lock.lock();
            if (INSTANCE == null) {
                INSTANCE = new ElmasClassLoader(ignoredPackages, ignoredClasses);
            }
            lock.unlock();
        }
        return INSTANCE;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String file = classNameMap.get(name);
        if (file == null) {
            throw new ClassNotFoundException();
        }
        ReadableByteChannel rbc = null;
        ByteBuffer dst;
        ByteArrayOutputStream bas = null;
        try {
            rbc = Files.newByteChannel(Paths.get(file), EnumSet.of(StandardOpenOption.READ));
            dst = ByteBuffer.allocate(1024);
            bas = new ByteArrayOutputStream();
            while (rbc.read(dst) != -1) {
                dst.flip();
                byte[] b = new byte[dst.remaining()];
                dst.rewind();
                dst.get(b);
                bas.write(b, 0, b.length);
                dst.clear();
            }
            byte[] res = bas.toByteArray();
            return defineClass(null, res, 0, res.length);
        } catch (IOException ignored) {
        } finally {
            try {
                if (bas != null) {
                    bas.close();
                }
                if (rbc != null) {
                    rbc.close();
                }
            } catch (IOException ignored) {
            }
        }
        throw new ClassNotFoundException();
    }

    private void init() {
        Path start = Paths.get(basePath);
        try {
            Files.walkFileTree(start, fileVisitor);
        } catch (IOException ignored) {
        }
    }


}
