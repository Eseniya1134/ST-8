import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class App {

    private static final String SITE = "http://www.papercdcase.com/";
    private static final Path DATA_FILE   = Paths.get("data", "data.txt");
    private static final Path RESULT_DIR  = Paths.get("result");
    private static final Path RESULT_FILE = RESULT_DIR.resolve("cd.pdf");

    private static final int      TRACK_LIMIT      = 16;
    private static final Duration PAGE_TIMEOUT     = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(45);
    private static final long     POLL_INTERVAL_MS = 400L;

    public static void main(String[] args) throws Exception {
        Album album = Album.fromFile(DATA_FILE);
        log("loaded album: " + album.artist + " — " + album.title
                + " (" + album.tracks.size() + " tracks)");

        Files.createDirectories(RESULT_DIR);
        cleanupOldPdfs(RESULT_DIR);

        WebDriver driver = launchBrowser(RESULT_DIR.toAbsolutePath().toString());
        try {
            WebDriverWait wait = new WebDriverWait(driver, PAGE_TIMEOUT);

            openHomePage(driver, wait);
            fillBasicFields(driver, album);
            fillTracks(driver, album.tracks);
            pickRadio(driver, "template", "jewel");
            pickRadio(driver, "size",     "a4");
            pickCheckbox(driver, "force_saveas");

            log("submitting form");
            driver.findElement(By.name("submit")).submit();

            waitForPdf(RESULT_DIR, RESULT_FILE);
            log("saved PDF → " + RESULT_FILE.toAbsolutePath());
        } finally {
            driver.quit();
        }
    }

    /* ---------- album loader ---------- */

    private static final class Album {
        final String artist;
        final String title;
        final List<String> tracks;

        private Album(String artist, String title, List<String> tracks) {
            this.artist = artist;
            this.title  = title;
            this.tracks = tracks;
        }

        static Album fromFile(Path path) throws IOException {
            List<String> rows = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                String s = line.strip();
                if (!s.isEmpty() && !s.startsWith("#")) rows.add(s);
            }
            if (rows.size() < 3) {
                throw new IllegalStateException(
                        "data.txt must contain artist, album and at least one track");
            }
            String artist = rows.get(0);
            String title  = rows.get(1);
            int end = Math.min(rows.size(), 2 + TRACK_LIMIT);
            List<String> tracks = new ArrayList<>(rows.subList(2, end));
            return new Album(artist, title, tracks);
        }
    }

    /* ---------- browser ---------- */

    private static WebDriver launchBrowser(String downloadDir) {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory",       downloadDir);
        prefs.put("download.prompt_for_download",     Boolean.FALSE);
        prefs.put("download.directory_upgrade",       Boolean.TRUE);
        prefs.put("plugins.always_open_pdf_externally", Boolean.TRUE);

        ChromeOptions opts = new ChromeOptions();
        opts.setPageLoadStrategy(PageLoadStrategy.EAGER);
        opts.addArguments("--ignore-certificate-errors",
                          "--disable-popup-blocking",
                          "--start-maximized");
        opts.setExperimentalOption("prefs", prefs);

        String binary = System.getenv("CHROME_BINARY");
        if (binary != null && !binary.isBlank()) {
            opts.setBinary(binary);
        }
        return new ChromeDriver(opts);
    }

    /* ---------- form steps ---------- */

    private static void openHomePage(WebDriver driver, WebDriverWait wait) {
        log("opening " + SITE);
        driver.get(SITE);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("artist")));
    }

    private static void fillBasicFields(WebDriver driver, Album a) {
        typeInto(driver, By.name("artist"), a.artist);
        typeInto(driver, By.name("title"),  a.title);
    }

    private static void fillTracks(WebDriver driver, List<String> tracks) {
        for (int i = 0; i < tracks.size(); i++) {
            typeInto(driver, By.name("track" + (i + 1)), tracks.get(i));
        }
    }

    private static void typeInto(WebDriver driver, By locator, String text) {
        WebElement el = driver.findElement(locator);
        el.clear();
        el.sendKeys(text);
    }

    private static void pickRadio(WebDriver driver, String group, String value) {
        String css = String.format("input[name='%s'][value='%s']", group, value);
        WebElement radio = driver.findElement(By.cssSelector(css));
        if (!radio.isSelected()) radio.click();
    }

    private static void pickCheckbox(WebDriver driver, String name) {
        WebElement box = driver.findElement(By.name(name));
        if (!box.isSelected()) box.click();
    }

    /* ---------- pdf handling ---------- */

    private static void cleanupOldPdfs(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.filter(App::isPdf).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static boolean isPdf(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf");
    }

    private static void waitForPdf(Path dir, Path destination)
            throws IOException, InterruptedException {

        Instant deadline = Instant.now().plus(DOWNLOAD_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<Path> found;
            try (Stream<Path> stream = Files.list(dir)) {
                found = stream.filter(App::isPdf).findFirst();
            }
            if (found.isPresent()) {
                Path src = found.get();
                if (!src.equals(destination)) {
                    Files.move(src, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new IllegalStateException("PDF was not downloaded within " + DOWNLOAD_TIMEOUT);
    }

    /* ---------- logging ---------- */

    private static void log(String message) {
        System.out.println("[" + Instant.now() + "] " + message);
    }
}
