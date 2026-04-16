package com.mycompany.core.models;

import java.util.List;
import java.util.Map;

/**
 * Merged content + analytics document for a single AEM page.
 * Stored in Pinecone as a vector with this data in metadata.
 */
public class PageDocument {

    // === Content fields ===
    private String pagePath;
    private String title;
    private String description;
    private List<String> tags;
    private String template;
    private String publishedDate;
    private String lastModified;
    private List<String> components;
    private int componentCount;
    private boolean hasHero;
    private boolean hasVideo;
    private boolean hasCta;
    private String heroTitle;
    private String heroCTAText;
    private String category;

    // === Analytics fields ===
    private String archetype;
    private int pageViews;
    private double bounceRate;
    private int avgTimeOnPage;
    private double heroCtr;
    private double conversionRate;
    private String topTrafficSource;
    private String topRegion;
    private Map<String, Double> deviceSplit;
    private List<Integer> monthlyViews;
    private double returningVisitorPct;

    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getPublishedDate() { return publishedDate; }
    public void setPublishedDate(String publishedDate) { this.publishedDate = publishedDate; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) { this.components = components; }

    public int getComponentCount() { return componentCount; }
    public void setComponentCount(int componentCount) { this.componentCount = componentCount; }

    public boolean isHasHero() { return hasHero; }
    public void setHasHero(boolean hasHero) { this.hasHero = hasHero; }

    public boolean isHasVideo() { return hasVideo; }
    public void setHasVideo(boolean hasVideo) { this.hasVideo = hasVideo; }

    public boolean isHasCta() { return hasCta; }
    public void setHasCta(boolean hasCta) { this.hasCta = hasCta; }

    public String getHeroTitle() { return heroTitle; }
    public void setHeroTitle(String heroTitle) { this.heroTitle = heroTitle; }

    public String getHeroCTAText() { return heroCTAText; }
    public void setHeroCTAText(String heroCTAText) { this.heroCTAText = heroCTAText; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getArchetype() { return archetype; }
    public void setArchetype(String archetype) { this.archetype = archetype; }

    public int getPageViews() { return pageViews; }
    public void setPageViews(int pageViews) { this.pageViews = pageViews; }

    public double getBounceRate() { return bounceRate; }
    public void setBounceRate(double bounceRate) { this.bounceRate = bounceRate; }

    public int getAvgTimeOnPage() { return avgTimeOnPage; }
    public void setAvgTimeOnPage(int avgTimeOnPage) { this.avgTimeOnPage = avgTimeOnPage; }

    public double getHeroCtr() { return heroCtr; }
    public void setHeroCtr(double heroCtr) { this.heroCtr = heroCtr; }

    public double getConversionRate() { return conversionRate; }
    public void setConversionRate(double conversionRate) { this.conversionRate = conversionRate; }

    public String getTopTrafficSource() { return topTrafficSource; }
    public void setTopTrafficSource(String topTrafficSource) { this.topTrafficSource = topTrafficSource; }

    public String getTopRegion() { return topRegion; }
    public void setTopRegion(String topRegion) { this.topRegion = topRegion; }

    public Map<String, Double> getDeviceSplit() { return deviceSplit; }
    public void setDeviceSplit(Map<String, Double> deviceSplit) { this.deviceSplit = deviceSplit; }

    public List<Integer> getMonthlyViews() { return monthlyViews; }
    public void setMonthlyViews(List<Integer> monthlyViews) { this.monthlyViews = monthlyViews; }

    public double getReturningVisitorPct() { return returningVisitorPct; }
    public void setReturningVisitorPct(double returningVisitorPct) { this.returningVisitorPct = returningVisitorPct; }

    /**
     * Builds the text that gets embedded into Pinecone.
     */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(nullSafe(title)).append("\n");
        sb.append("Description: ").append(nullSafe(description)).append("\n");
        sb.append("Category: ").append(nullSafe(category)).append("\n");
        if (tags != null && !tags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }
        if (components != null && !components.isEmpty()) {
            sb.append("Components: ").append(String.join(", ", components)).append("\n");
        }
        sb.append("Component Count: ").append(componentCount).append("\n");
        if (hasHero) {
            sb.append("Has Hero: yes");
            if (heroTitle != null && !heroTitle.isEmpty()) {
                sb.append(", Hero Title: \"").append(heroTitle).append("\"");
            }
            if (heroCTAText != null && !heroCTAText.isEmpty()) {
                sb.append(", Hero CTA: \"").append(heroCTAText).append("\"");
            }
            sb.append("\n");
        } else {
            sb.append("Has Hero: no\n");
        }
        sb.append("Has Video: ").append(hasVideo ? "yes" : "no").append("\n");
        sb.append("Page Views (Q4): ").append(pageViews).append("\n");
        if (monthlyViews != null && !monthlyViews.isEmpty()) {
            sb.append("Monthly Views Trend: ").append(monthlyViews.toString().replaceAll("[\\[\\]]", "")).append("\n");
        }
        sb.append("Bounce Rate: ").append(Math.round(bounceRate * 100)).append("%\n");
        sb.append("Avg Time on Page: ").append(avgTimeOnPage).append(" seconds\n");
        sb.append("Hero CTR: ").append(Math.round(heroCtr * 100)).append("%\n");
        sb.append("Conversion Rate: ").append(String.format("%.1f", conversionRate * 100)).append("%\n");
        sb.append("Top Traffic Source: ").append(nullSafe(topTrafficSource)).append("\n");
        sb.append("Top Region: ").append(nullSafe(topRegion)).append("\n");
        sb.append("Returning Visitors: ").append(Math.round(returningVisitorPct * 100)).append("%\n");
        if (publishedDate != null) {
            sb.append("Published: ").append(publishedDate).append("\n");
        }
        if (lastModified != null) {
            sb.append("Last Modified: ").append(lastModified).append("\n");
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
