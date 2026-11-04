package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Saves the user's events in the app's private storage on the phone. */
public final class EventStore {
    private static final String FILE_NAME = "events.txt";

    private final AtomicFile file;
    private final Context context;

    public EventStore(Context context) {
        this.context = context.getApplicationContext();
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /** Returns the saved events, or an empty list if nothing has been saved yet. */
    public List<Event> load() throws IOException {
        try {
            return EventCodec.decode(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException firstLaunch) {
            return new ArrayList<>();
        }
    }

    /** Removes the saved event with the same id as {@code event}, and the rest of its group. */
    public void delete(Event event) throws IOException {
        save(EventGroups.withoutGroupOf(load(), event));
    }

    /** Removes every one of {@code events} (matched by id) in one save. */
    public void deleteAll(Collection<Event> events) throws IOException {
        save(EventGroups.withoutEvents(load(), events));
    }

    /** Replaces the saved event that has the same id as {@code event}, or adds it if none does. */
    public void put(Event event) throws IOException {
        List<Event> events = load();
        events.removeIf(saved -> saved.getId().equals(event.getId()));
        events.add(event);
        save(events);
    }

    /** The saved event with this id, or null if there is none. */
    public Event find(String id) throws IOException {
        for (Event event : load()) {
            if (event.getId().equals(id)) {
                return event;
            }
        }
        return null;
    }

    public void save(List<Event> events) throws IOException {
        FileOutputStream output = file.startWrite();
        try {
            output.write(EventCodec.encode(events).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException e) {
            file.failWrite(output);
            throw e;
        }
        // What is saved decides the next notification, so set it again.
        NotificationScheduler.reschedule(context);
    }
}
