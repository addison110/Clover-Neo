package org.floens.chan.core.site;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageSearchTest {
    private static final String IMAGE = "https://i.4cdn.org/g/1792502739356166.jpg";

    @Test
    public void allEnginesUseHttpsAndEncodeTheImageUrl() {
        for (ImageSearch engine : ImageSearch.engines) {
            String url = engine.getUrl(IMAGE);
            assertTrue(engine.getName(), url.startsWith("https://"));
            assertTrue(engine.getName(), url.endsWith("https%3A%2F%2Fi.4cdn.org%2Fg%2F1792502739356166.jpg"));
        }
    }

    @Test
    public void queryCharactersInTheImageUrlStayInsideTheParameter() {
        for (ImageSearch engine : ImageSearch.engines) {
            String url = engine.getUrl("https://example.org/image.php?id=1&size=full");
            assertFalse(engine.getName(), url.contains("&size="));
        }
    }

    @Test
    public void engineIdsAreUnique() {
        Set<Integer> ids = new HashSet<>();
        for (ImageSearch engine : ImageSearch.engines) {
            assertTrue(engine.getName(), ids.add(engine.getId()));
        }
        assertEquals(ImageSearch.engines.size(), ids.size());
    }

    @Test
    public void yandexUsesTheUrlParameter() {
        for (ImageSearch engine : ImageSearch.engines) {
            if (engine.getName().equals("Yandex")) {
                assertTrue(engine.getUrl(IMAGE).contains("rpt=imageview&url="));
                return;
            }
        }
        throw new AssertionError("Yandex engine missing");
    }
}
