package com.github.bingoohuang.westcache.utils;

import com.github.bingoohuang.westcache.base.WestCacheItem;
import com.github.bingoohuang.westcache.spring.SpringAppContext;
import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @author bingoohuang [bingoohuang@gmail.com] Created on 2017/1/3.
 */
@Slf4j
public class Redis {
    public static final String PREFIX = "westcache:";
    private static JedisCommands jedis = createtJedisCommands(
            "127.0.0.1", 6379, 10);

    public static void setJedis(JedisCommands settedJedis) {
        jedis = settedJedis;
    }

    public static JedisCommands getJedis() {
        return jedis;
    }

    public static JedisCommands getRedis(WestCacheOption option) {
        String redisBean = option.getSpecs().get("redisBean");
        if (StringUtils.isNotEmpty(redisBean)) {
            JedisCommands bean = SpringAppContext.getBean(redisBean);
            if (bean != null) return bean;
        }

        val bean = SpringAppContext.getBean(JedisCommands.class);
        if (bean != null) return bean;

        return jedis;
    }

    public static JedisCommands createtJedisCommands(
            String host, int port, int maxTotal) {
        val poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);

        val pool = new JedisPool(poolConfig, host, port);
        return proxyJedisCommands(pool);
    }

    public static JedisCommands proxyJedisCommands(JedisPool pool) {
        return (JedisCommands) Proxy.newProxyInstance(
                JedisInvocationHandler.class.getClassLoader(),
                new Class[]{JedisCommands.class},
                new JedisInvocationHandler(pool));
    }

    @SneakyThrows
    public static boolean waitRedisLock(JedisCommands redis, String lockKey) {
        int maxWaitTimes = 10;
        while (maxWaitTimes-- > 0) {
            Long lock = redis.setnx(lockKey, "lock");
            if (lock == 1L) return true;

            Thread.sleep(50L);
        }
        return false;
    }

    public static WestCacheItem getWestCacheItem(WestCacheOption option,
                                                 JedisCommands redis,
                                                 String redisKey) {
        val jsonValue = redis.get(redisKey);
        if (jsonValue == null) return null;

        val value = FastJsons.parse(jsonValue, option.getMethod());
        return new WestCacheItem(value);
    }

    public static String expirePut(WestCacheOption option,
                                   JedisCommands redis,
                                   String redisKey,
                                   WestCacheItem item) {
        val json = FastJsons.json(item.orNull());
        val expireKey = "expireAfterWrite";
        val expireWrite = option.getSpecs().get(expireKey);
        if (isBlank(expireWrite)) return redis.set(redisKey, json);

        val duration = Durations.parse(expireKey, expireWrite, SECONDS);
        log.info("redis set {}={} in expire {} seconds", redisKey, json, duration);
        val result = redis.set(redisKey, json);
        redis.expire(redisKey, (int) duration);

        return result;
    }

    @AllArgsConstructor
    public static class JedisInvocationHandler implements InvocationHandler {
        final JedisPool pool;

        @Override
        public Object invoke(Object proxy,
                             Method method,
                             Object[] args) throws Throwable {
            val jedis = pool.getResource();
            @Cleanup val i = new Closeable() {
                @Override @SneakyThrows public void close() {
                    jedis.close();
                }
            };

            return method.invoke(jedis, args);
        }
    }
}
