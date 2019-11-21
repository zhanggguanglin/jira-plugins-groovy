package ru.mail.jira.plugins.groovy.util.cl;

import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginState;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class DelegatingClassLoader extends ClassLoader {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Logger logger = LoggerFactory.getLogger(DelegatingClassLoader.class);
    //todo: maybe change to Map<String, String(plugin key)> with LoadingCache<String, ClassLoader>
    private final LoadingCache<String, Class<?>> classCache = Caffeine
        .newBuilder()
        .maximumSize(500)
        .expireAfterAccess(20, TimeUnit.MINUTES)
        .recordStats()
        .build(this::doLoadClass);

    private final Map<String, ClassLoaderEntry> classLoaders;

    public DelegatingClassLoader() {
        super(null);
        this.classLoaders = new LinkedHashMap<>();
        this.classLoaders.put("__local", new ClassLoaderEntry(new WeakReference<>(Thread.currentThread().getContextClassLoader()), true));
        //loader for jira core classes
        this.classLoaders.put("__jira", new ClassLoaderEntry(new WeakReference<>(ClassLoaderUtil.getJiraClassLoader()), true));
    }

    public void ensureAvailability(Set<Plugin> plugins) {
        if (plugins.size() == 0) {
            return;
        }

        for (Plugin plugin : plugins) {
            if (plugin.getPluginState() != PluginState.ENABLED) {
                throw new RuntimeException("Plugin " + plugin.getKey() + " is not enabled");
            }
            ClassLoader pluginClassLoader = plugin.getClassLoader();

            ClassLoaderEntry currentLoader = classLoaders.get(plugin.getKey());
            if (currentLoader == null || currentLoader.reference.get() != pluginClassLoader) {
                registerClassLoader(plugin.getKey(), pluginClassLoader, true);
            }
        }
    }

    public void registerClassLoader(String key, ClassLoader classLoader, boolean cacheable) {
        Lock wLock = rwLock.writeLock();
        wLock.lock();
        try {
            ClassLoaderEntry prevValue = classLoaders.put(key, new ClassLoaderEntry(new WeakReference<>(classLoader), cacheable));
            if (cacheable && (prevValue == null || prevValue.reference.get() != classLoader)) {
                classCache.invalidateAll();
            }
        } finally {
            wLock.unlock();
        }
    }

    public void unloadPlugin(String key) {
        Lock wLock = rwLock.writeLock();
        wLock.lock();
        try {
            classLoaders.remove(key);
            classCache.invalidateAll();
        } finally {
            wLock.unlock();
        }
    }

    public void flushCache() {
        Lock wLock = rwLock.writeLock();
        wLock.lock();
        try {
            classCache.invalidateAll();
        } finally {
            wLock.unlock();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> aClass = classCache.get(name);

        if (aClass == null) {
            aClass = doLoadClass(name, true);
        }

        if (aClass == null) {
            throw new ClassNotFoundException(name);
        }

        return aClass;
    }

    private Class<?> doLoadClass(String name) {
        return doLoadClass(name, true);
    }

    private Class<?> doLoadClass(String name, boolean cacheable) {
        Lock lock = rwLock.readLock();
        lock.lock();
        try {
            for (Map.Entry<String, ClassLoaderEntry> entry : classLoaders.entrySet()) {
                ClassLoaderEntry e = entry.getValue();
                if (e.cacheable == cacheable) {
                    try {
                        ClassLoader classLoader = e.reference.get();

                        if (classLoader == null) {
                            logger.warn("classloader for {} is gone", entry.getKey());
                            continue;
                        }

                        return classLoader.loadClass(name);
                    } catch (ClassNotFoundException ignore) {
                    }
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected URL findResource(String name) {
        return ClassLoaderUtil.getCurrentPluginClassLoader().getResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return ClassLoaderUtil.getCurrentPluginClassLoader().getResources(name);
    }

    public ClassLoader getJiraClassLoader() {
        return this.classLoaders.get("__jira").reference.get();
    }

    @AllArgsConstructor
    private static class ClassLoaderEntry {
        private final Reference<ClassLoader> reference;
        private final boolean cacheable;
    }
}
