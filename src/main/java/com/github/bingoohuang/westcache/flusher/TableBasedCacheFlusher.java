package com.github.bingoohuang.westcache.flusher;

import com.github.bingoohuang.westcache.base.WestCacheItem;
import com.github.bingoohuang.westcache.utils.FastJsons;
import com.github.bingoohuang.westcache.utils.Keys;
import com.github.bingoohuang.westcache.utils.WestCacheOption;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author bingoohuang [bingoohuang@gmail.com] Created on 2016/12/28.
 */
@Slf4j
public abstract class TableBasedCacheFlusher extends SimpleCacheFlusher {
    volatile List<WestCacheFlusherBean> tableRows = Lists.newArrayList();
    volatile ScheduledFuture<?> scheduledFuture;

    @Getter volatile long lastExecuted = -1;
    Cache<String, Optional<Map<String, String>>> prefixDirectCache
            = CacheBuilder.newBuilder().build();

    @Override @SneakyThrows
    public boolean isKeyEnabled(WestCacheOption option, String cacheKey) {
        tryStartup(option, cacheKey);

        val bean = findBean(cacheKey);
        return bean != null;
    }

    private void tryStartup(WestCacheOption option, String cacheKey) {
        if (lastExecuted != -1) return;

        synchronized (this) {
            if (lastExecuted != -1) return;

            startupRotateChecker(option, cacheKey);
        }
    }

    @SneakyThrows
    public void cancelRotateChecker() {
        val future = scheduledFuture;
        scheduledFuture = null;

        if (future == null) return;
        if (future.isDone()) return;

        future.cancel(false);
        while (!future.isDone()) {
            Thread.sleep(500L);
        }

        lastExecuted = -1;
    }

    @Override
    public Optional<Object> getDirectValue(
            WestCacheOption option, String cacheKey) {
        val bean = findBean(cacheKey);
        if (bean == null) return Optional.absent();

        if (!"direct".equals(bean.getValueType())) {
            return Optional.absent();
        }

        if ("full".equals(bean.getKeyMatch())) {
            Object value = readDirectValue(option, bean, DirectValueType.FULL);
            return Optional.fromNullable(value);
        }

        if ("prefix".equals(bean.getKeyMatch())) {
            val subKey = cacheKey.substring(bean.getCacheKey().length() + 1);
            Object value = readSubDirectValue(option, bean, subKey);
            return Optional.fromNullable(value);
        }

        return Optional.absent();
    }

    protected abstract List<WestCacheFlusherBean> queryAllBeans();

    protected abstract Object readDirectValue(WestCacheOption option,
                                              WestCacheFlusherBean bean,
                                              DirectValueType type);

    @SneakyThrows
    private <T> T readSubDirectValue(final WestCacheOption option,
                                     final WestCacheFlusherBean bean,
                                     String subKey) {
        val loader = new Callable<Optional<Map<String, String>>>() {
            @Override
            public Optional<Map<String, String>> call() throws Exception {
                val map = readDirectValue(option, bean, DirectValueType.SUB);
                return Optional.fromNullable((Map<String, String>) map);
            }
        };
        val optional = prefixDirectCache.get(bean.getCacheKey(), loader);
        if (!optional.isPresent()) return null;

        String json = optional.get().get(subKey);
        if (json == null) return null;

        return FastJsons.parse(json, option.getMethod());
    }

    protected WestCacheFlusherBean findBean(String cacheKey) {
        for (val bean : tableRows) {
            if ("full".equals(bean.getKeyMatch())) {
                if (bean.getCacheKey().equals(cacheKey)) return bean;
            }
        }

        for (val bean : tableRows) {
            if ("prefix".equals(bean.getKeyMatch())) {
                if (Keys.isPrefix(cacheKey, bean.getCacheKey())) return bean;
            }
        }

        return null;
    }

    protected void startupRotateChecker(final WestCacheOption option,
                                        final String cacheKey) {
        firstCheckBeans(option, cacheKey);

        val intervalMillis = option.getConfig().rotateIntervalMillis();
        val executor = option.getConfig().executorService();
        scheduledFuture = executor.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                checkBeans(option, cacheKey);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @SneakyThrows
    protected Object firstCheckBeans(final WestCacheOption option,
                                     String cacheKey) {
        val snapshot = option.getSnapshot();
        if (snapshot == null) return checkBeans(option, cacheKey);

        return futureGet(option, cacheKey);
    }

    @SneakyThrows
    private Object futureGet(final WestCacheOption option,
                             final String cacheKey) {
        Future<Object> future = option.getConfig().executorService()
                .submit(new Callable<Object>() {
                    @Override public Object call() throws Exception {
                        return checkBeans(option, cacheKey);
                    }
                });

        long timeout = option.getConfig().timeoutMillisToSnapshot();
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            log.info("get first check beans {} timeout in " +
                    "{} millis, try snapshot", cacheKey, timeout);
            WestCacheItem result = option.getSnapshot().
                    readSnapshot(option, cacheKey + ".tableflushers");
            log.info("got {} snapshot {}", cacheKey,
                    result != null ? result.getObject() : " non-exist");
            if (result != null) return 1;
        }

        return future.get();
    }

    @SneakyThrows
    protected int checkBeans(WestCacheOption option, String cacheKey) {
        log.debug("start rotating check");
        val beans = queryAllBeans();

        if (lastExecuted == -1) {
            tableRows = beans;
            saveSnapshot(option, cacheKey);
        } else if (beans.equals(tableRows)) {
            log.debug("no changes detected");
        } else {
            diff(tableRows, beans, option);
            tableRows = beans;
        }
        lastExecuted = System.currentTimeMillis();
        return 1;
    }

    private void saveSnapshot(WestCacheOption option, String cacheKey) {
        val snapshot = option.getSnapshot();
        if (snapshot == null) return;

        option.getSnapshot().saveSnapshot(option,
                cacheKey + ".tableflushers",
                new WestCacheItem(tableRows));
    }

    protected void diff(List<WestCacheFlusherBean> table,
                        List<WestCacheFlusherBean> beans,
                        WestCacheOption option) {
        val flushKeys = getDiffFlushKeys(table, beans);
        if (flushKeys.isEmpty()) return;

        Set<String> prefixKeys = Sets.newHashSet();
        Set<String> fullKeys = Sets.newHashSet();
        getFlushKeys(flushKeys, prefixKeys, fullKeys);

        log.debug("flush full keys:{}", fullKeys);
        log.debug("flush prefix keys:{}", prefixKeys);

        for (String fullKey : fullKeys) {
            flush(option, fullKey);
        }

        for (String prefixKey : prefixKeys) {
            flushPrefix(prefixKey);
        }
    }

    private Map<String, WestCacheFlusherBean> getDiffFlushKeys(
            List<WestCacheFlusherBean> table,
            List<WestCacheFlusherBean> beans) {
        Map<String, WestCacheFlusherBean> flushKeys = Maps.newHashMap();
        for (val bean : table) {
            val found = find(bean, beans);
            if (found == null ||
                    found.getValueVersion() != bean.getValueVersion()) {
                flushKeys.put(bean.getCacheKey(), bean);
            }
        }
        return flushKeys;
    }

    private void getFlushKeys(Map<String, WestCacheFlusherBean> flushKeys,
                              Set<String> prefixKeys, Set<String> fullKeys) {
        for (val key : getRegistry().asMap().keySet()) {
            if (flushKeys.containsKey(key)) {
                fullKeys.add(key);
                continue;
            }

            for (val bean : flushKeys.values()) {
                if (!"prefix".equals(bean.getKeyMatch())) continue;
                if (Keys.isPrefix(key, bean.getCacheKey())) {
                    fullKeys.add(key);
                    prefixKeys.add(bean.getCacheKey());
                }
            }
        }
    }

    protected void flushPrefix(String prefixKey) {
        prefixDirectCache.invalidate(prefixKey);
    }

    protected WestCacheFlusherBean find(WestCacheFlusherBean bean,
                                        List<WestCacheFlusherBean> beans) {
        for (val newbean : beans) {
            if (bean.getCacheKey().equals(newbean.getCacheKey()))
                return newbean;
        }
        return null;
    }
}