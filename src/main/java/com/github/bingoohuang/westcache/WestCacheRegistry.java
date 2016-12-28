package com.github.bingoohuang.westcache;

import com.github.bingoohuang.westcache.base.*;
import com.github.bingoohuang.westcache.cachekey.DefaultKeyer;
import com.github.bingoohuang.westcache.cachekey.SimpleKeyer;
import com.github.bingoohuang.westcache.config.DefaultWestCacheConfig;
import com.github.bingoohuang.westcache.flusher.DiamondCacheFlusher;
import com.github.bingoohuang.westcache.flusher.NoneCacheFlusher;
import com.github.bingoohuang.westcache.flusher.SimpleCacheFlusher;
import com.github.bingoohuang.westcache.manager.DiamondCacheManager;
import com.github.bingoohuang.westcache.manager.GuavaCacheManager;
import com.github.bingoohuang.westcache.registry.RegistryTemplate;
import com.github.bingoohuang.westcache.snapshot.FileCacheSnapshot;
import com.github.bingoohuang.westcache.utils.WestCacheOption;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * @author bingoohuang [bingoohuang@gmail.com] Created on 2016/12/23.
 */
@UtilityClass
public class WestCacheRegistry {
    RegistryTemplate<WestCacheConfig> configRegistry = new RegistryTemplate<WestCacheConfig>();

    static {
        register("default", new DefaultWestCacheConfig());
    }

    public void register(String configName, WestCacheConfig config) {
        configRegistry.register(configName, config);
    }

    public void registerForcely(String configName, WestCacheConfig config) {
        configRegistry.registerForcely(configName, config);
    }

    public void deregisterConfig(String configName) {
        configRegistry.deregister(configName);
    }

    public WestCacheConfig getConfig(String configName) {
        return configRegistry.get(configName);
    }

    RegistryTemplate<WestCacheFlusher> flusherRegistry = new RegistryTemplate<WestCacheFlusher>();

    static {
        register("none", new NoneCacheFlusher());
        register("simple", new SimpleCacheFlusher());
        register("diamond", new DiamondCacheFlusher());
    }

    public void register(String flusherName, WestCacheFlusher flusher) {
        flusherRegistry.register(flusherName, flusher);
    }

    public void registerForcely(String flusherName, WestCacheFlusher flusher) {
        flusherRegistry.registerForcely(flusherName, flusher);
    }

    public void deregisterFlusher(String flusherName) {
        flusherRegistry.deregister(flusherName);
    }

    public WestCacheFlusher getFlusher(String flusherName) {
        return flusherRegistry.get(flusherName);
    }

    public void flush(WestCacheOption option,
                      Object bean,
                      String methodName,
                      Object... args) {
        val keyStrategy = option.getKeyStrategy();
        String cacheKey = keyStrategy.getCacheKey(option, methodName, bean, args);
        option.getFlusher().flush(cacheKey);
    }

    RegistryTemplate<WestCacheManager> managerRegistry = new RegistryTemplate<WestCacheManager>();

    static {
        register("guava", new GuavaCacheManager());
        register("diamond", new DiamondCacheManager());
    }

    public void register(String managerName, WestCacheManager manager) {
        managerRegistry.register(managerName, manager);
    }

    public void registerForcely(String managerName, WestCacheManager manager) {
        managerRegistry.registerForcely(managerName, manager);
    }

    public void deregisterManager(String managerName) {
        managerRegistry.deregister(managerName);
    }

    public WestCacheManager getManager(String managerName) {
        return managerRegistry.get(managerName);
    }

    RegistryTemplate<WestCacheSnapshot> snapshotRegistry = new RegistryTemplate<WestCacheSnapshot>();

    static {
        register("file", new FileCacheSnapshot());
    }

    public void register(String snapshotName, WestCacheSnapshot snapshot) {
        snapshotRegistry.register(snapshotName, snapshot);
    }

    public void registerForcely(String snapshotName, WestCacheSnapshot snapshot) {
        snapshotRegistry.registerForcely(snapshotName, snapshot);
    }

    public void deregisterSnapshot(String snapshotName) {
        snapshotRegistry.deregister(snapshotName);
    }

    public WestCacheSnapshot getSnapshot(String snapshotName) {
        return snapshotRegistry.get(snapshotName);
    }

    RegistryTemplate<WestCacheKeyer> keyStrategyRegistry = new RegistryTemplate<WestCacheKeyer>();

    static {
        register("default", new DefaultKeyer());
        register("simple", new SimpleKeyer());
    }

    public void register(String keyStrategyName, WestCacheKeyer keyStrategy) {
        keyStrategyRegistry.register(keyStrategyName, keyStrategy);
    }

    public void registerForcely(String keyStrategyName, WestCacheKeyer keyStrategy) {
        keyStrategyRegistry.registerForcely(keyStrategyName, keyStrategy);
    }

    public void deregisterKeyStrategy(String keyStrategyName) {
        keyStrategyRegistry.deregister(keyStrategyName);
    }

    public WestCacheKeyer getKeyStrategy(String keyStrategyName) {
        return keyStrategyRegistry.get(keyStrategyName);
    }
}