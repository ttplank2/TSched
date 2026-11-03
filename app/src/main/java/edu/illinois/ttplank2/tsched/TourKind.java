package edu.illinois.ttplank2.tsched;

/**
 * The guided tours, each a list of steps. A step lights up one real control on the screen (the
 * first of its target ids that is showing) and explains it, or has no target and is just a card
 * in the middle of the screen.
 */
public enum TourKind {
    /** The calendar: its controls one at a time, then on to the Add Event tour. */
    HOME(R.string.tour_show_add,
            step(R.string.tour_home_welcome_title, R.string.tour_home_welcome_body),
            step(R.string.tour_home_days_title, R.string.tour_home_days_body, R.id.day_pager, R.id.week_scroll),
            step(R.string.tour_home_add_title, R.string.tour_home_add_body, R.id.add_event_button),
            step(R.string.tour_home_free_title, R.string.tour_home_free_body, R.id.free_events_button),
            step(R.string.tour_home_filter_title, R.string.tour_home_filter_body, R.id.filter_button),
            step(R.string.tour_home_snap_title, R.string.tour_home_snap_body, R.id.snap_to_day_button),
            step(R.string.tour_home_settings_title, R.string.tour_home_settings_body, R.id.settings_button),
            step(R.string.tour_home_events_title, R.string.tour_home_events_body)),
    /** The Add Event form: each part of it, down to Save. */
    ADD(R.string.tutorial_done,
            step(R.string.tour_add_kind_title, R.string.tour_add_kind_body, R.id.kind_group),
            step(R.string.tour_add_name_title, R.string.tour_add_name_body, R.id.name_input),
            step(R.string.tour_add_color_title, R.string.tour_add_color_body, R.id.color_button),
            step(R.string.tour_add_days_title, R.string.tour_add_days_body, R.id.days_section),
            step(R.string.tour_add_times_title, R.string.tour_add_times_body, R.id.fixed_section),
            step(R.string.tour_add_save_title, R.string.tour_add_save_body, R.id.save_button));

    /** One step of a tour. */
    static final class Step {
        /** The ids of the control to light up: the first one showing wins; empty for no control. */
        final int[] targetIds;
        final int titleRes;
        final int bodyRes;

        private Step(int titleRes, int bodyRes, int[] targetIds) {
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.targetIds = targetIds;
        }
    }

    final Step[] steps;
    /** What the last step's button says: it ends the tour, or leads on to the next one. */
    final int lastButtonRes;

    TourKind(int lastButtonRes, Step... steps) {
        this.lastButtonRes = lastButtonRes;
        this.steps = steps;
    }

    private static Step step(int titleRes, int bodyRes, int... targetIds) {
        return new Step(titleRes, bodyRes, targetIds);
    }
}
