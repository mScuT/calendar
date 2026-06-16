package com.example.meetings;

import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests - Assignment point 5.
 *
 * Starts the real application on a random port (@SpringBootTest with RANDOM_PORT) and drives it
 * through a real headless Chrome with Selenium. It runs on a separate in-memory database (e2edb),
 * which is cleared before each test, so the browser writes (which are not rolled back) do not leak
 * into the other tests or between these two flows. Covered flows:
 *  - a single user registers, logs in, proposes a meeting, and sees it on the calendar;
 *  - two users: one invites the other, who accepts, and the meeting becomes confirmed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:e2edb;DB_CLOSE_DELAY=-1")
class EndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MeetingRepository meetingRepository;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUp() {
        // There is no @Transactional rollback for a real server, so clear the data ourselves.
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,1024");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    /** A single user registers, logs in, proposes a meeting, and sees it on the calendar. */
    @Test
    void user_canRegisterLogInProposeAMeetingAndSeeItOnTheCalendar() {
        register("alice", "alice@example.pt", "secret");
        login("alice", "secret");

        proposeMeeting("Portugal vs Congo", "2026-07-01T18:00", "2026-07-01T20:00", "");

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "Portugal vs Congo"));
        assertThat(driver.getPageSource()).contains("Portugal vs Congo");
    }

    /** Two users: alice invites bob, bob accepts, and the meeting becomes confirmed. */
    @Test
    void twoUsers_inviteAndAccept_marksMeetingConfirmed() {
        // bob must exist before alice can invite him.
        register("bob", "bob@example.pt", "secret");
        register("alice", "alice@example.pt", "secret");

        // alice proposes a meeting inviting bob -> tentative while bob is still pending.
        login("alice", "secret");
        proposeMeeting("Portugal vs Congo", "2026-07-01T18:00", "2026-07-01T20:00", "bob");
        assertThat(driver.getPageSource()).contains("Portugal vs Congo").contains("tentative");
        logout();

        // bob logs in, sees the pending invite, and accepts it.
        login("bob", "secret");
        assertThat(driver.getPageSource()).contains("Portugal vs Congo").contains("pending");
        driver.findElement(By.xpath("//button[normalize-space()='Accept']")).click();

        // everyone has accepted -> the meeting is now confirmed.
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "confirmed"));
        assertThat(driver.getPageSource()).contains("Portugal vs Congo").contains("confirmed");
    }

    /** Proposing with the end before the start is rejected: the error is shown and nothing is saved. */
    @Test
    void proposingWithEndBeforeStart_showsErrorAndSavesNothing() {
        register("alice", "alice@example.pt", "secret");
        login("alice", "secret");

        // end is before start; no client-side rule blocks this, so the server must reject it.
        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys("Portugal vs Congo");
        setDateTimeLocal(By.id("start"), "2026-07-01T20:00");
        setDateTimeLocal(By.id("end"), "2026-07-01T18:00");
        driver.findElement(By.xpath("//button[normalize-space()='Propose']")).click();

        // The propose page comes back with the error message...
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "End time must be after start time"));

        // ...and nothing was saved to the calendar.
        driver.get(url("/calendar"));
        assertThat(driver.getPageSource()).contains("No meetings yet");
    }

    // --- helpers ---

    private void register(String username, String email, String password) {
        driver.get(url("/register"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.xpath("//button[normalize-space()='Register']")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    private void login(String username, String password) {
        driver.get(url("/login"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.xpath("//button[normalize-space()='Sign in']")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    private void logout() {
        driver.findElement(By.xpath("//button[normalize-space()='Sign out']")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    private void proposeMeeting(String title, String start, String end, String invitees) {
        driver.get(url("/meetings/new"));
        driver.findElement(By.id("title")).sendKeys(title);
        setDateTimeLocal(By.id("start"), start);
        setDateTimeLocal(By.id("end"), end);
        if (!invitees.isEmpty()) {
            driver.findElement(By.id("invitees")).sendKeys(invitees);
        }
        driver.findElement(By.xpath("//button[normalize-space()='Propose']")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    private void setDateTimeLocal(By locator, String isoValue) {
        WebElement input = driver.findElement(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", input, isoValue);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
