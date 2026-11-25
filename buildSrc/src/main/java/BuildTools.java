import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class BuildTools {
    private BuildTools() {}
    public static String getBuildTime() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC).format(Instant.now());
    }
    public static boolean isNonStable(String version) {
        String upper = version.toUpperCase(Locale.ROOT);
        boolean stable = upper.contains("RELEASE") || upper.contains("FINAL") || upper.contains("GA")
            || version.matches("^[0-9,.v-]+(-r)?$");
        return !stable;
    }
}
