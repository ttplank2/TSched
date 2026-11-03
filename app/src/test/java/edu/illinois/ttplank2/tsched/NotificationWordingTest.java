package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A notification's title is the app's name, and its text is the event's name, a dash, and the start time. */
public class NotificationWordingTest {
    private static String strings() throws IOException {
        File file = new File("src/main/res/values/strings.xml");
        if (!file.exists()) {
            file = new File("app/src/main/res/values/strings.xml");
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String stringNamed(String name) throws IOException {
        Matcher match = Pattern.compile("<string name=\"" + name + "\">(.*?)</string>").matcher(strings());
        assertTrue(name + " is missing", match.find());
        return match.group(1);
    }

    @Test
    public void theNotificationTextIsTheEventNameADashAndTheTime() throws IOException {
        assertEquals("%1$s - %2$s", stringNamed("notification_text"));
    }

    @Test
    public void theNotificationTitleIsTheAppName() throws IOException {
        assertEquals("TSched", stringNamed("app_name"));
    }

    @Test
    public void thereAreNoLeftoverWordingsFromTheOldNotification() throws IOException {
        for (String old : new String[] {"notification_text_with_place", "notification_text_now",
                "notification_text_now_with_place"}) {
            assertTrue(old + " should be gone", !strings().contains("name=\"" + old + "\""));
        }
    }
}
