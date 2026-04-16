package com.mycompany.core.services.impl;

import com.google.gson.Gson;
import com.mycompany.core.models.PageDocument;
import com.mycompany.core.services.MockAnalyticsService;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component(service = MockAnalyticsService.class, immediate = true)
public class MockAnalyticsServiceImpl implements MockAnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(MockAnalyticsServiceImpl.class);
    private static final String ANALYTICS_ROOT = "/var/content-intelligence/analytics";
    private static final String SERVICE_USER = "content-intelligence-service";
    private static final Gson GSON = new Gson();

    private static final String[] ARCHETYPES = {
        "star_performer", "hidden_gem", "traffic_trap", "steady_performer", "dead_weight"
    };
    private static final String[] TRAFFIC_SOURCES = {"organic", "paid", "social", "email", "direct"};
    private static final String[] REGIONS = {"US", "APAC", "EMEA", "LATAM"};

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void enrichWithAnalytics(PageDocument doc) {
        long seed = (long) doc.getPagePath().hashCode();
        Random rng = new Random(seed);

        String archetype = ARCHETYPES[Math.abs((int) (seed % ARCHETYPES.length))];
        doc.setArchetype(archetype);

        switch (archetype) {
            case "star_performer":
                doc.setPageViews(randomInt(rng, 30000, 60000));
                doc.setBounceRate(randomDouble(rng, 0.20, 0.35));
                doc.setAvgTimeOnPage(randomInt(rng, 200, 400));
                doc.setHeroCtr(doc.isHasHero() ? randomDouble(rng, 0.10, 0.18) : 0.0);
                doc.setConversionRate(randomDouble(rng, 0.08, 0.15));
                doc.setMonthlyViews(trendingUp(rng, 4, doc.getPageViews()));
                doc.setReturningVisitorPct(randomDouble(rng, 0.35, 0.55));
                break;

            case "hidden_gem":
                doc.setPageViews(randomInt(rng, 2000, 8000));
                doc.setBounceRate(randomDouble(rng, 0.20, 0.30));
                doc.setAvgTimeOnPage(randomInt(rng, 250, 450));
                doc.setHeroCtr(doc.isHasHero() ? randomDouble(rng, 0.12, 0.20) : 0.0);
                doc.setConversionRate(randomDouble(rng, 0.10, 0.18));
                doc.setMonthlyViews(trendingFlat(rng, 4, doc.getPageViews()));
                doc.setReturningVisitorPct(randomDouble(rng, 0.45, 0.65));
                break;

            case "traffic_trap":
                doc.setPageViews(randomInt(rng, 25000, 50000));
                doc.setBounceRate(randomDouble(rng, 0.55, 0.75));
                doc.setAvgTimeOnPage(randomInt(rng, 30, 90));
                doc.setHeroCtr(doc.isHasHero() ? randomDouble(rng, 0.02, 0.05) : 0.0);
                doc.setConversionRate(randomDouble(rng, 0.01, 0.03));
                doc.setMonthlyViews(trendingFlat(rng, 4, doc.getPageViews()));
                doc.setReturningVisitorPct(randomDouble(rng, 0.15, 0.25));
                break;

            case "steady_performer":
                doc.setPageViews(randomInt(rng, 10000, 25000));
                doc.setBounceRate(randomDouble(rng, 0.35, 0.50));
                doc.setAvgTimeOnPage(randomInt(rng, 120, 250));
                doc.setHeroCtr(doc.isHasHero() ? randomDouble(rng, 0.06, 0.10) : 0.0);
                doc.setConversionRate(randomDouble(rng, 0.04, 0.08));
                doc.setMonthlyViews(trendingFlat(rng, 4, doc.getPageViews()));
                doc.setReturningVisitorPct(randomDouble(rng, 0.30, 0.45));
                break;

            case "dead_weight":
            default:
                doc.setPageViews(randomInt(rng, 500, 3000));
                doc.setBounceRate(randomDouble(rng, 0.60, 0.80));
                doc.setAvgTimeOnPage(randomInt(rng, 20, 60));
                doc.setHeroCtr(doc.isHasHero() ? randomDouble(rng, 0.01, 0.03) : 0.0);
                doc.setConversionRate(randomDouble(rng, 0.005, 0.02));
                doc.setMonthlyViews(trendingDown(rng, 4, doc.getPageViews()));
                doc.setReturningVisitorPct(randomDouble(rng, 0.15, 0.30));
                break;
        }

        doc.setTopTrafficSource(TRAFFIC_SOURCES[rng.nextInt(TRAFFIC_SOURCES.length)]);
        doc.setTopRegion(REGIONS[rng.nextInt(REGIONS.length)]);

        Map<String, Double> deviceSplit = new HashMap<>();
        double desktop = randomDouble(rng, 0.45, 0.65);
        double tablet = randomDouble(rng, 0.05, 0.12);
        deviceSplit.put("desktop", round2(desktop));
        deviceSplit.put("mobile", round2(1.0 - desktop - tablet));
        deviceSplit.put("tablet", round2(tablet));
        doc.setDeviceSplit(deviceSplit);
    }

    @Override
    public PageDocument loadFromJcr(String pagePath) {
        Map<String, Object> authInfo = Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            String storagePath = ANALYTICS_ROOT + "/" + hashPath(pagePath);
            Resource resource = resolver.getResource(storagePath);
            if (resource == null) return null;

            String json = resource.getValueMap().get("analyticsJson", String.class);
            if (json == null || json.isEmpty()) return null;

            return GSON.fromJson(json, PageDocument.class);
        } catch (Exception e) {
            LOG.error("Failed to load analytics for {}: {}", pagePath, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void saveToJcr(PageDocument doc) {
        Map<String, Object> authInfo = Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            String storagePath = ANALYTICS_ROOT + "/" + hashPath(doc.getPagePath());

            Map<String, Object> props = new HashMap<>();
            props.put("jcr:primaryType", "nt:unstructured");
            props.put("analyticsJson", GSON.toJson(doc));
            props.put("pagePath", doc.getPagePath());

            Resource resource = ResourceUtil.getOrCreateResource(resolver, storagePath,
                    props, "nt:unstructured", false);

            ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
            if (mvm != null) {
                mvm.put("analyticsJson", GSON.toJson(doc));
                mvm.put("pagePath", doc.getPagePath());
            }
            resolver.commit();
        } catch (Exception e) {
            LOG.error("Failed to save analytics for {}: {}", doc.getPagePath(), e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int randomInt(Random rng, int min, int max) {
        return min + rng.nextInt(max - min);
    }

    private double randomDouble(Random rng, double min, double max) {
        return round2(min + rng.nextDouble() * (max - min));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private List<Integer> trendingUp(Random rng, int months, int total) {
        List<Integer> list = new ArrayList<>();
        int base = total / (months + 2);
        for (int i = 0; i < months; i++) {
            list.add(base + rng.nextInt(base / 4) + (i * base / 5));
        }
        return list;
    }

    private List<Integer> trendingFlat(Random rng, int months, int total) {
        List<Integer> list = new ArrayList<>();
        int base = total / months;
        for (int i = 0; i < months; i++) {
            list.add(base + rng.nextInt(base / 5) - base / 10);
        }
        return list;
    }

    private List<Integer> trendingDown(Random rng, int months, int total) {
        List<Integer> list = new ArrayList<>();
        int base = total / (months - 1);
        for (int i = months; i > 0; i--) {
            list.add(Math.max(50, base * i / months + rng.nextInt(50)));
        }
        Collections.reverse(list);
        return list;
    }

    private String hashPath(String pagePath) {
        // Simple, filesystem-safe hash
        return String.valueOf(Math.abs(pagePath.hashCode()));
    }
}
