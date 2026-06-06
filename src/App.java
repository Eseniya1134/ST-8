import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class App {

    private static final String URL = "http://www.papercdcase.com/";
    private static final int MAX_TRACKS = 16;

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("data/data.txt"));
        if (lines.size() < 2) {
            throw new IllegalStateException("data/data.txt must contain at least artist and title");
        }
        String artist = lines.get(0);
        String title = lines.get(1);
        List<String> tracks = new ArrayList<>();
        for (int i = 2; i < lines.size() && tracks.size() < MAX_TRACKS; i++) {
            String t = lines.get(i).trim();
            if (!t.isEmpty()) tracks.add(t);
        }

        File resultDir = new File("result");
        if (!resultDir.exists()) resultDir.mkdirs();
        String downloadDir = resultDir.getAbsolutePath();

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("plugins.always_open_pdf_externally", true);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--ignore-certificate-errors");
        options.setExperimentalOption("prefs", prefs);

        WebDriver webDriver = new ChromeDriver(options);
        try {
            webDriver.get(URL);

            webDriver.findElement(By.xpath("//input[@name='artist']")).sendKeys(artist);
            webDriver.findElement(By.xpath("//input[@name='title']")).sendKeys(title);

            for (int i = 0; i < tracks.size(); i++) {
                String xp = "//input[@name='track" + (i + 1) + "']";
                webDriver.findElement(By.xpath(xp)).sendKeys(tracks.get(i));
            }

            webDriver.findElement(By.xpath("//input[@name='template' and @value='jewel']")).click();
            webDriver.findElement(By.xpath("//input[@name='size' and @value='a4']")).click();

            WebElement forceSaveAs = webDriver.findElement(By.xpath("//input[@name='force_saveas']"));
            if (!forceSaveAs.isSelected()) forceSaveAs.click();

            WebElement btn = webDriver.findElement(By.xpath("//input[@type='image' and @name='submit']"));
            btn.submit();

            Path target = Paths.get("result", "cd.pdf");
            Path downloaded = waitForPdf(resultDir.toPath(), 30_000);
            if (downloaded != null && !downloaded.getFileName().toString().equals("cd.pdf")) {
                Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("PDF saved to: " + target.toAbsolutePath());
        } finally {
            webDriver.quit();
        }
    }

    private static Path waitForPdf(Path dir, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Stream<Path> s = Files.list(dir)) {
                Optional<Path> p = s.filter(x -> x.getFileName().toString().toLowerCase().endsWith(".pdf"))
                        .findFirst();
                if (p.isPresent()) return p.get();
            }
            Thread.sleep(500);
        }
        return null;
    }
}
