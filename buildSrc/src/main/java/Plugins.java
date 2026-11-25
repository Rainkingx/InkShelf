public final class Plugins {
    private Plugins() {}
    public static final String androidApplication = "com.android.application";
    public static final String kapt = "kapt";
    public static final String kotlinParcelize = "kotlin-parcelize";
    public static final String kotlinAndroid = "android";
    public static final String jetbrainsKotlin = "org.jetbrains.kotlin.android";
    public static final String kotlinSerialization = "org.jetbrains.kotlin.plugin.serialization";
    public static final PluginClass gradleVersions = new PluginClass("com.github.ben-manes.versions", "0.52.0");
    public static final PluginClass kotlinter = new PluginClass("org.jmailen.kotlinter", "5.0.2");

    public static final class PluginClass {
        public final String name;
        public final String version;
        public PluginClass(String name, String version) {
            this.name = name;
            this.version = version;
        }
        @Override public String toString() { return name + ":" + version; }
    }
}
