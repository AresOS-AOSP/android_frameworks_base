/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.crdroid;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PixelDeviceRepository {

    private static final String TAG = "PixelDeviceRepository";
    private static final String CACHE_KEY = "pi_pixel_device_cache";
    private static final long CACHE_TTL = 30L * 24 * 60 * 60 * 1000; // 30 days
    private static final String GOOGLE_URL = "https://developer.android.com";

    private static volatile List<PixelProfile> sMemoryCache = null;
    private static final Object sFetchLock = new Object();

    public static final class PixelProfile {
        public final String codename;
        public final String model;
        public final String brand;
        public final String device;
        public final String product;
        public final String fingerprint;
        public final String buildId;
        public final String securityPatch;
        public final long fetchedAt;

        public PixelProfile(String codename, String model, String brand, String device,
                String product, String fingerprint, String buildId,
                String securityPatch, long fetchedAt) {
            this.codename     = codename;
            this.model        = model;
            this.brand        = brand;
            this.device       = device;
            this.product      = product;
            this.fingerprint  = fingerprint;
            this.buildId      = buildId;
            this.securityPatch = securityPatch;
            this.fetchedAt    = fetchedAt;
        }

        // Kotlin-style getters for compatibility with existing call sites
        public String getCodename()      { return codename; }
        public String getModel()         { return model; }
        public String getBrand()         { return brand; }
        public String getDevice()        { return device; }
        public String getProduct()       { return product; }
        public String getFingerprint()   { return fingerprint; }
        public String getBuildId()       { return buildId; }
        public String getSecurityPatch() { return securityPatch; }
        public long   getFetchedAt()     { return fetchedAt; }
    }

    // Must be updated when new Pixel codenames are released — devices not listed here
    // will be silently skipped during network profile fetch in fetchFromNetwork()
    public static final Set<String> KNOWN_CODENAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    // Pixel 7 series
                    "panther", "cheetah", "lynx",
                    // Pixel 7 Fold
                    "felix",
                    // Pixel Tablet
                    "tangorpro",
                    // Pixel 8 series
                    "shiba", "husky", "akita",
                    // Pixel 9 series
                    "tokay", "caiman", "komodo", "comet", "tegu",
                    // Pixel 10 series
                    "frankel", "blazer", "mustang", "rango", "stallion"
            )));

    public static final Map<String, String> DEVICE_MODEL_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("panther",   "Pixel 7");
        m.put("cheetah",   "Pixel 7 Pro");
        m.put("lynx",      "Pixel 7a");
        m.put("felix",     "Pixel Fold");
        m.put("tangorpro", "Pixel Tablet");
        m.put("shiba",     "Pixel 8");
        m.put("husky",     "Pixel 8 Pro");
        m.put("akita",     "Pixel 8a");
        m.put("tokay",     "Pixel 9");
        m.put("caiman",    "Pixel 9 Pro");
        m.put("komodo",    "Pixel 9 Pro XL");
        m.put("comet",     "Pixel 9 Pro Fold");
        m.put("tegu",      "Pixel 9a");
        m.put("frankel",   "Pixel 10");
        m.put("blazer",    "Pixel 10 Pro");
        m.put("mustang",   "Pixel 10 Pro XL");
        m.put("rango",     "Pixel 10 Pro Fold");
        m.put("stallion",  "Pixel 10a");
        DEVICE_MODEL_MAP = Collections.unmodifiableMap(m);
    }

    // Hardcoded fallback profiles — only used when network fails AND cache is empty
    public static final List<PixelProfile> FALLBACK_PROFILES;
    static {
        List<PixelProfile> f = new ArrayList<>();
        f.add(new PixelProfile("mustang",   "Pixel 10 Pro XL",   "google", "mustang",   "mustang",
                "google/mustang/mustang:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("rango",     "Pixel 10 Pro Fold", "google", "rango",     "rango",
                "google/rango/rango:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("blazer",    "Pixel 10 Pro",      "google", "blazer",    "blazer",
                "google/blazer/blazer:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("frankel",   "Pixel 10",          "google", "frankel",   "frankel",
                "google/frankel/frankel:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("stallion",  "Pixel 10a",         "google", "stallion",  "stallion",
                "google/stallion/stallion:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("tangorpro", "Pixel Tablet",      "google", "tangorpro", "tangorpro",
                "google/tangorpro/tangorpro:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("komodo",    "Pixel 9 Pro XL",    "google", "komodo",    "komodo",
                "google/komodo/komodo:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("caiman",    "Pixel 9 Pro",       "google", "caiman",    "caiman",
                "google/caiman/caiman:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("comet",     "Pixel 9 Pro Fold",  "google", "comet",     "comet",
                "google/comet/comet:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("tokay",     "Pixel 9",           "google", "tokay",     "tokay",
                "google/tokay/tokay:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("tegu",      "Pixel 9a",          "google", "tegu",      "tegu",
                "google/tegu/tegu:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("husky",     "Pixel 8 Pro",       "google", "husky",     "husky",
                "google/husky/husky:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("shiba",     "Pixel 8",           "google", "shiba",     "shiba",
                "google/shiba/shiba:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("akita",     "Pixel 8a",          "google", "akita",     "akita",
                "google/akita/akita:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("felix",     "Pixel Fold",        "google", "felix",     "felix",
                "google/felix/felix:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("cheetah",   "Pixel 7 Pro",       "google", "cheetah",   "cheetah",
                "google/cheetah/cheetah:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("lynx",      "Pixel 7a",          "google", "lynx",      "lynx",
                "google/lynx/lynx:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        f.add(new PixelProfile("panther",   "Pixel 7",           "google", "panther",   "panther",
                "google/panther/panther:16/CP1A.260505.005/15081906:user/release-keys",
                "CP1A.260505.005", "2026-05-05", 0L));
        FALLBACK_PROFILES = Collections.unmodifiableList(f);
    }

    private PixelDeviceRepository() {}

    /**
     * Returns cached profiles if fresh, otherwise fetches from network.
     * Must be called from a background thread.
     * Falls back to hardcoded profiles if network fails and cache is empty.
     */
    public static List<PixelProfile> getProfiles(Context context, boolean forceRefresh) {
        synchronized (sFetchLock) {
            List<PixelProfile> cached = readCache(context);
            long fetchedAt = cached.isEmpty() ? 0L : cached.get(0).fetchedAt;
            boolean stale = cached.isEmpty() ||
                    (System.currentTimeMillis() - fetchedAt) > CACHE_TTL;

            if (!forceRefresh && !stale) return cached;

            List<PixelProfile> fresh = Collections.emptyList();
            try {
                fresh = fetchFromNetwork();
            } catch (Exception e) {
                Log.w(TAG, "Network fetch failed: " + e.getMessage());
            }

            if (!fresh.isEmpty()) {
                writeCache(context, fresh);
                return fresh;
            } else {
                return cached.isEmpty() ? FALLBACK_PROFILES : cached;
            }
        }
    }

    public static List<PixelProfile> getProfiles(Context context) {
        return getProfiles(context, false);
    }

    /**
     * Returns a single profile by codename from cache.
     * Falls back to mustang (mobile) or tangorpro (tablet) if not found.
     * Safe to call from any thread.
     */
    public static PixelProfile getProfileByCodename(Context context, String codename,
            boolean isTablet) {
        try {
            List<PixelProfile> cached = sMemoryCache;
            if (cached == null) {
                cached = readCache(context);
                sMemoryCache = cached;
            }
            String defaultCodename = isTablet ? "tangorpro" : "mustang";
            PixelProfile result = findByCodename(cached, codename);
            if (result == null) result = findByCodename(cached, defaultCodename);
            if (result == null) result = findByCodename(FALLBACK_PROFILES, codename);
            if (result == null) result = findByCodename(FALLBACK_PROFILES, defaultCodename);
            return result;
        } catch (Exception e) {
            Log.w(TAG, "getProfileByCodename failed, using fallback: " + e.getMessage());
            return findByCodename(FALLBACK_PROFILES, isTablet ? "tangorpro" : "mustang");
        }
    }

    private static PixelProfile findByCodename(List<PixelProfile> list, String codename) {
        for (PixelProfile p : list) {
            if (p.codename.equals(codename)) return p;
        }
        return null;
    }

    public static List<PixelProfile> readCache(Context context) {
        try {
            String json = Settings.Secure.getString(context.getContentResolver(), CACHE_KEY);
            if (json == null || json.isEmpty()) return Collections.emptyList();
            JSONArray arr = new JSONArray(json);
            List<PixelProfile> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    result.add(new PixelProfile(
                            o.getString("codename"),
                            o.getString("model"),
                            o.getString("brand"),
                            o.getString("device"),
                            o.getString("product"),
                            o.getString("fingerprint"),
                            o.getString("buildId"),
                            o.getString("securityPatch"),
                            o.getLong("fetchedAt")
                    ));
                } catch (Exception ignored) {}
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "Cache read failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static void writeCache(Context context, List<PixelProfile> profiles) {
        sMemoryCache = profiles;
        try {
            JSONArray arr = new JSONArray();
            for (PixelProfile p : profiles) {
                JSONObject o = new JSONObject();
                o.put("codename",      p.codename);
                o.put("model",         p.model);
                o.put("brand",         p.brand);
                o.put("device",        p.device);
                o.put("product",       p.product);
                o.put("fingerprint",   p.fingerprint);
                o.put("buildId",       p.buildId);
                o.put("securityPatch", p.securityPatch);
                o.put("fetchedAt",     p.fetchedAt);
                arr.put(o);
            }
            String json = arr.toString();
            if (json.length() > 7500) {
                Log.w(TAG, "Cache JSON too large (" + json.length() + " chars), trimming to newest entries");
                // Drop oldest entries (tail of list) until it fits
                while (arr.length() > 1) {
                    arr.remove(arr.length() - 1);
                    json = arr.toString();
                    if (json.length() <= 7500) break;
                }
            }
            Settings.Secure.putString(context.getContentResolver(), CACHE_KEY, json);
        } catch (Exception e) {
            Log.w(TAG, "Cache write failed: " + e.getMessage());
        }
    }

    private static List<PixelProfile> fetchFromNetwork() throws Exception {
        String versionsHtml = readUrl(GOOGLE_URL + "/about/versions");
        List<Integer> knownVersions = new ArrayList<>();
        java.util.regex.Matcher vm = java.util.regex.Pattern.compile(
                "https://developer\\.android\\.com/about/versions/(\\d+)")
                .matcher(versionsHtml);
        Set<Integer> seen = new HashSet<>();
        while (vm.find()) {
            int v = Integer.parseInt(vm.group(1));
            if (seen.add(v)) knownVersions.add(v);
        }
        Collections.sort(knownVersions, Collections.reverseOrder());

        if (knownVersions.isEmpty()) return Collections.emptyList();

        for (int version : knownVersions) {
            try {
                String otaHtml = readUrl(GOOGLE_URL + "/about/versions/" + version + "/download-ota");
                java.util.regex.Matcher om = java.util.regex.Pattern.compile(
                        "href=\"(https://dl\\.google\\.com/[^\"]*ota/([^/\"]+_beta)[^\"]*?)\"")
                        .matcher(otaHtml);

                List<String[]> otaList = new ArrayList<>();
                while (om.find()) otaList.add(new String[]{om.group(1), om.group(2)});
                if (otaList.isEmpty()) continue;

                long now = System.currentTimeMillis();
                List<PixelProfile> profiles = new ArrayList<>();
                Set<String> seenDevices = new HashSet<>();

                for (String[] entry : otaList) {
                    String otaUrl = entry[0];
                    String product = entry[1];
                    String device = product.replace("_beta", "");
                    if (!KNOWN_CODENAMES.contains(device)) continue;
                    if (!seenDevices.add(device)) continue;

                    try {
                        String partial = fetchPartialUrl(otaUrl, 8192);
                        java.util.regex.Matcher fm =
                                java.util.regex.Pattern.compile("post-build=(.*)")
                                        .matcher(partial);
                        if (!fm.find()) continue;
                        String fingerprint = fm.group(1).trim();

                        java.util.regex.Matcher sm =
                                java.util.regex.Pattern.compile("security-patch-level=(.*)")
                                        .matcher(partial);
                        if (!sm.find()) continue;
                        String securityPatch = sm.group(1).trim();

                        String[] fpParts = fingerprint.split("/");
                        if (fpParts.length < 4) continue;
                        String buildId = fpParts[3];

                        String model = DEVICE_MODEL_MAP.containsKey(device)
                                ? DEVICE_MODEL_MAP.get(device) : device;

                        profiles.add(new PixelProfile(
                                device, model, "google", device,
                                product.replace("_beta", ""),
                                fingerprint, buildId, securityPatch, now));
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse OTA for " + device + ": " + e.getMessage());
                    }
                }

                if (!profiles.isEmpty()) return profiles;
            } catch (Exception e) {
                // try next version
            }
        }
        return Collections.emptyList();
    }

    private static String fetchPartialUrl(String url, int maxBytes) throws Exception {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        InputStream input = conn.getInputStream();
        try {
            byte[] buf = new byte[512];
            StringBuilder sb = new StringBuilder();
            int total = 0;
            while (total < maxBytes) {
                int read = input.read(buf);
                if (read == -1) break;
                sb.append(new String(buf, 0, read, StandardCharsets.ISO_8859_1));
                total += read;
            }
            return sb.toString();
        } finally {
            input.close();
        }
    }

    private static String readUrl(String url) throws Exception {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        InputStream input = conn.getInputStream();
        try {
            byte[] bytes = input.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
