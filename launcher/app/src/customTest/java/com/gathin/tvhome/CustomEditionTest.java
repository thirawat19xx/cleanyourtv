package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CustomEditionTest {
    private static final String VIDEO = "org.example.video/org.example.video.Home";
    private static final String MUSIC = "com.spotify.tv.android/com.spotify.tv.android.Main";
    private static final String NEW_APP = "org.example.newapp/org.example.newapp.Home";

    @Test public void newInstallsStayOutOfHomeUntilExplicitlySelected() {
        List<String> favorites = Arrays.asList(MUSIC, VIDEO);
        assertEquals(favorites, CustomOrder.selected(favorites, Arrays.asList(NEW_APP, VIDEO, MUSIC)));
    }
    @Test public void removingEveryFavoriteDoesNotRestoreDefaults() {
        assertTrue(CustomOrder.selected(CustomOrder.decode(""), Arrays.asList(VIDEO, MUSIC)).isEmpty());
    }
    @Test public void temporarilyUnavailableFavoriteReturnsInItsOriginalPosition() {
        List<String> saved = CustomOrder.decode(CustomOrder.encode(Arrays.asList(MUSIC, VIDEO)));
        assertEquals(Collections.singletonList(VIDEO), CustomOrder.selected(saved, Collections.singletonList(VIDEO)));
        assertEquals(Arrays.asList(MUSIC, VIDEO), CustomOrder.selected(saved, Arrays.asList(VIDEO, MUSIC)));
    }
    @Test public void allAppsKeepsCustomOrderAndAppendsNewInstalls() {
        assertEquals(Arrays.asList(MUSIC, VIDEO, NEW_APP), CustomOrder.reconcile(Arrays.asList(MUSIC, VIDEO), Arrays.asList(NEW_APP, VIDEO, MUSIC)));
    }
    @Test public void allAppsOmitsUninstalledEntriesWithoutDuplicatingNewOnes() {
        assertEquals(Arrays.asList(VIDEO, NEW_APP), CustomOrder.reconcile(Arrays.asList(MUSIC, VIDEO), Arrays.asList(VIDEO, NEW_APP, VIDEO)));
    }
    @Test public void savedChoicesRoundTripWithNestedActivityNames() {
        List<String> source = Arrays.asList(VIDEO, "org.example.a/.Outer$Inner", MUSIC);
        assertEquals(source, CustomOrder.decode(CustomOrder.encode(source)));
    }
    @Test public void malformedAndDuplicateChoicesCannotInjectExtraEntries() {
        assertEquals(Arrays.asList(VIDEO, MUSIC), CustomOrder.decode(VIDEO + "\n" + VIDEO + "\n../escape\n" + MUSIC + "\nnot a component"));
    }
    @Test public void movingAtEitherEdgeKeepsAValidOrder() {
        List<String> order = Arrays.asList(VIDEO, MUSIC);
        assertEquals(order, CustomOrder.move(order, VIDEO, -1));
        assertEquals(order, CustomOrder.move(order, MUSIC, 1));
        assertEquals(Arrays.asList(MUSIC, VIDEO), CustomOrder.move(order, VIDEO, 1));
        assertEquals(Arrays.asList(VIDEO, MUSIC), order);
    }
    @Test public void offlineRetryIsRateLimitedEvenWithoutAnyCachedData() {
        assertFalse(CustomWeatherData.shouldRefresh(1000000, 0, 999000, 1800000));
        assertTrue(CustomWeatherData.shouldRefresh(1300000, 0, 999000, 1800000));
    }
    @Test public void cacheRefreshesOnlyWhenSelectedIntervalExpires() {
        assertFalse(CustomWeatherData.shouldRefresh(2000000, 1000000, 0, 1800000));
        assertTrue(CustomWeatherData.shouldRefresh(2800000, 1000000, 0, 1800000));
        assertFalse(CustomWeatherData.shouldRefresh(2800000, 1000000, 0, 3600000));
    }
    @Test public void movingTheSystemClockBackwardDoesNotFreezeWeatherForever() {
        assertTrue(CustomWeatherData.shouldRefresh(1000, 2000000, 2000000, 1800000));
    }
    @Test public void standardTemperaturePointsConvertForBothUnits() {
        assertEquals(32, CustomWeatherData.degrees(0, true));
        assertEquals(212, CustomWeatherData.degrees(100, true));
        assertEquals(-40, CustomWeatherData.degrees(-40, true));
        assertEquals(28, CustomWeatherData.degrees(27.6, false));
    }
    @Test public void coordinatesRejectInvalidValuesButAcceptBangkokAndPoles() {
        assertTrue(CustomWeatherData.coordinates(13.7563, 100.5018));
        assertTrue(CustomWeatherData.coordinates(-90, 180));
        assertFalse(CustomWeatherData.coordinates(Double.NaN, 0));
        assertFalse(CustomWeatherData.coordinates(91, 0));
        assertFalse(CustomWeatherData.coordinates(0, Double.POSITIVE_INFINITY));
    }
    @Test public void coldShowersAreNotMistakenForRainIcons() {
        assertEquals("❄", CustomWeatherData.icon(85, true));
        assertEquals("⛈", CustomWeatherData.icon(95, true));
        assertNotEquals(CustomWeatherData.icon(0, true), CustomWeatherData.icon(0, false));
    }
    @Test public void unrelatedPlayersAndLookalikePackagesAreNotSpotify() {
        assertTrue(CustomMediaPolicy.spotify("com.spotify.tv.android"));
        assertTrue(CustomMediaPolicy.spotify("com.spotify.music"));
        assertFalse(CustomMediaPolicy.spotify("com.spotify.music.fake"));
        assertFalse(CustomMediaPolicy.spotify("com.google.android.youtube"));
        assertFalse(CustomMediaPolicy.spotify(null));
    }
    @Test public void pausedTracksDoNotAdvanceAndPlayingTracksRespectDuration() {
        assertEquals(1000, CustomMediaPolicy.position(1000, 100, 1, 1100, 5000, false));
        assertEquals(2000, CustomMediaPolicy.position(1000, 100, 1, 1100, 5000, true));
        assertEquals(5000, CustomMediaPolicy.position(4900, 100, 1, 1100, 5000, true));
    }
    @Test public void unknownDurationAndInvalidPlaybackRatesRemainSafe() {
        assertEquals(0, CustomMediaPolicy.position(-1, 0, 1, 1000, 0, true));
        assertEquals(100, CustomMediaPolicy.position(100, 1, Float.NaN, 1000, 0, true));
        assertEquals(2100, CustomMediaPolicy.position(100, 1, 2, 1001, 0, true));
    }
    @Test public void ordinaryFullHdWallpaperKeepsItsOriginalDetail() {
        assertEquals(1, CustomImagePolicy.sample(1920, 1080, 1920, 1080));
    }
    @Test public void extremelyWideAndTallPhotosCannotForceAHundredMegabyteDecode() {
        for (int[] dimensions : new int[][]{{50000, 1000}, {1000, 50000}}) {
            int sample = CustomImagePolicy.sample(dimensions[0], dimensions[1], 1920, 1080);
            assertTrue(sample > 1);
            long decoded = ((dimensions[0] + sample - 1L) / sample) * ((dimensions[1] + sample - 1L) / sample);
            assertTrue(decoded <= CustomImagePolicy.MAX_DECODE_PIXELS);
        }
    }
    @Test public void largeCameraPhotosAreSampledBeforeCropping() {
        int sample = CustomImagePolicy.sample(7500, 6500, 1920, 1080);
        assertTrue(sample > 1);
        assertTrue((7500L / sample) * (6500L / sample) <= CustomImagePolicy.MAX_DECODE_PIXELS);
    }
}
