package com.github.bingoohuang.westcache;

import com.github.bingoohuang.westcache.flusher.WestCacheFlusherBean;
import com.github.bingoohuang.westcache.outofbox.TableCacheFlusher;
import com.github.bingoohuang.westcache.utils.FastJsons;
import com.github.bingoohuang.westcache.utils.Helper;
import com.github.bingoohuang.westcache.utils.Redis;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.truth.Truth.assertThat;

/**
 * @author bingoohuang [bingoohuang@gmail.com] Created on 2016/12/28.
 */
public class TableCacheFlusherTest {
    static TableCacheFlusher flusher;
    static volatile long getCitiesCalledTimes;

    public static class MyLoader implements Callable {
        @Override public Object call() throws Exception {
            return "HaHa, I'm a demo only";
        }
    }

    @BeforeClass
    public static void beforeClass() {
        flusher = Helper.setupTableFlusherForTest();

        service.firstPush();
    }

    @WestCacheable(flusher = "table", keyer = "simple", snapshot = "file")
    public static abstract class TitaService {
        public String firstPush() {
            return "first";
        }

        public String tita() {
            return "" + System.currentTimeMillis();
        }

        public abstract String directValue();

        public String getCities(String provinceCode) {
            ++getCitiesCalledTimes;
            return provinceCode + System.currentTimeMillis();
        }

        public abstract String getCities2(String provinceCode);

        public abstract String specs();

        public abstract String specsRedis();
    }

    public static TitaService service = WestCacheFactory.create(TitaService.class);

    @Test @SneakyThrows
    public void tita() {
        val tita1 = service.tita();

        val cacheKey = "TableCacheFlusherTest.TitaService.tita";
        val bean = new WestCacheFlusherBean(cacheKey, "full", 0, "none", null);

        long lastExecuted = flusher.getLastExecuted();
        flusher.getDao().addBean(bean);
        Helper.waitFlushRun(flusher, lastExecuted);

        val tita2 = service.tita();
        val tita3 = service.tita();
        assertThat(tita2).isNotEqualTo(tita1);
        assertThat(tita2).isSameAs(tita3);

        lastExecuted = flusher.getLastExecuted();
        flusher.getDao().upgradeVersion(cacheKey);
        Helper.waitFlushRun(flusher, lastExecuted);

        val tita4 = service.tita();
        val tita5 = service.tita();
        assertThat(tita4).isNotEqualTo(tita3);
        assertThat(tita4).isSameAs(tita5);
    }

    @Test @SneakyThrows
    public void directValue() {
        val cacheKey = "TableCacheFlusherTest.TitaService.directValue";
        val bean = new WestCacheFlusherBean(cacheKey, "full", 0,
                "direct", null);

        long lastExecuted = flusher.getLastExecuted();
        flusher.getDao().addBean(bean);
        flusher.getDao().updateDirectValue(cacheKey, "\"helllo bingoo\"");
        Helper.waitFlushRun(flusher, lastExecuted);

        val tita1 = service.directValue();
        assertThat(tita1).isNotEqualTo("hello bingoo");

        val tita2 = service.directValue();
        assertThat(tita2).isSameAs(tita1);
    }

    @Test @SneakyThrows
    public void getCities() {
        val prefix = "TableCacheFlusherTest.TitaService.getCities";
        val bean = new WestCacheFlusherBean(prefix, "prefix", 0,
                "none", null);

        long lastExecuted = flusher.getLastExecuted();
        flusher.getDao().addBean(bean);
        Helper.waitFlushRun(flusher, lastExecuted);

        String jiangSuCities1 = service.getCities("JiangSu");
        String jiangXiCities1 = service.getCities("JiangXi");
        assertThat(getCitiesCalledTimes).isEqualTo(2);
        assertThat(jiangSuCities1).isNotEqualTo(jiangXiCities1);

        String jiangSuCities11 = service.getCities("JiangSu");
        String jiangXiCities11 = service.getCities("JiangXi");
        assertThat(getCitiesCalledTimes).isEqualTo(2);
        assertThat(jiangSuCities11).isSameAs(jiangSuCities1);
        assertThat(jiangXiCities11).isSameAs(jiangXiCities1);

        lastExecuted = flusher.getLastExecuted();
        flusher.getDao().upgradeVersion(prefix);
        Helper.waitFlushRun(flusher, lastExecuted);

        String jiangSuCities2 = service.getCities("JiangSu");
        String jiangXiCities2 = service.getCities("JiangXi");
        assertThat(getCitiesCalledTimes).isEqualTo(4);
        assertThat(jiangSuCities2).isNotEqualTo(jiangSuCities1);
        assertThat(jiangXiCities2).isNotEqualTo(jiangXiCities1);
    }

    @Test @SneakyThrows
    public void getCitiesWithDirectValue() {
        val prefix = "TableCacheFlusherTest.TitaService.getCities2";
        val bean = new WestCacheFlusherBean(prefix, "prefix", 0,
                "direct", null);

        Map<String, String> directValue = Maps.newHashMap();
        directValue.put("JiangSu", "XXX");
        directValue.put("JiangXi", "YYY");
        String json = FastJsons.json(directValue);

        long lastExecuted = flusher.getLastExecuted();
        flusher.getDao().addBean(bean);
        flusher.getDao().updateDirectValue(prefix, json);
        Helper.waitFlushRun(flusher, lastExecuted);

        String jiangSuCities1 = service.getCities2("JiangSu");
        String jiangXiCities1 = service.getCities2("JiangXi");
        assertThat(jiangSuCities1).isEqualTo("XXX");
        assertThat(jiangXiCities1).isEqualTo("YYY");

        String jiangSuCities11 = service.getCities2("JiangSu");
        String jiangXiCities11 = service.getCities2("JiangXi");
        assertThat(jiangSuCities11).isSameAs(jiangSuCities1);
        assertThat(jiangXiCities11).isSameAs(jiangXiCities1);

        lastExecuted = flusher.getLastExecuted();
        flusher.getDao().upgradeVersion(prefix);
        Helper.waitFlushRun(flusher, lastExecuted);

        String jiangSuCities2 = service.getCities2("JiangSu");
        String jiangXiCities2 = service.getCities2("JiangXi");
        assertThat(jiangSuCities2).isNotSameAs(jiangSuCities1);
        assertThat(jiangXiCities2).isNotSameAs(jiangXiCities1);

        Map<String, String> directValue2 = Maps.newHashMap();
        directValue2.put("JiangSu", "XXX111");
        directValue2.put("JiangXi", "YYY222");
        String json2 = FastJsons.json(directValue2);

        lastExecuted = flusher.getLastExecuted();
        flusher.getDao().updateDirectValue(prefix, json2);
        Helper.waitFlushRun(flusher, lastExecuted);

        String jiangSuCitiesA = service.getCities2("JiangSu");
        String jiangXiCitiesA = service.getCities2("JiangXi");
        assertThat(jiangSuCitiesA).isEqualTo("XXX111");
        assertThat(jiangXiCitiesA).isEqualTo("YYY222");

        String jiangSuCitiesA1 = service.getCities2("JiangSu");
        String jiangXiCitiesA1 = service.getCities2("JiangXi");
        assertThat(jiangSuCitiesA1).isSameAs(jiangSuCitiesA);
        assertThat(jiangXiCitiesA1).isSameAs(jiangXiCitiesA);
    }

    @Test @SneakyThrows
    public void specs() {
        val prefix = "TableCacheFlusherTest.TitaService.specs";
        val bean = new WestCacheFlusherBean(prefix, "full", 0,
                "direct", "readBy=loader;loaderClass=com.github.bingoohuang.westcache.TableCacheFlusherTest$MyLoader");

        addConfigBean(bean);

        val r1 = service.specs();
        assertThat(r1).isEqualTo("HaHa, I'm a demo only");
    }

    private void addConfigBean(WestCacheFlusherBean bean) throws InterruptedException {
        long lastExecuted = flusher.getLastExecuted();
        flusher.getDao().addBean(bean);
        Helper.waitFlushRun(flusher, lastExecuted);
    }

    @Test @SneakyThrows
    public void specsRedis() {
        val prefix = "TableCacheFlusherTest.TitaService.specsRedis";
        val bean = new WestCacheFlusherBean(prefix, "full", 0,
                "direct", "readBy=redis");

        Redis.getJedis().set(Redis.PREFIX + prefix, "\"I am redis body\"");

        addConfigBean(bean);

        val r1 = service.specsRedis();
        assertThat(r1).isEqualTo("I am redis body");
    }

}
