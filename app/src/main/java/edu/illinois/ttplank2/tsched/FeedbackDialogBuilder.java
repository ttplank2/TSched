package edu.illinois.ttplank2.tsched;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The app's popup builder: a tap on anything pressable inside the popup ticks, like on screens. */
public class FeedbackDialogBuilder extends MaterialAlertDialogBuilder {
    public FeedbackDialogBuilder(@NonNull Context context) {
        super(context);
    }

    public FeedbackDialogBuilder(@NonNull Context context, int overrideThemeResId) {
        super(context, overrideThemeResId);
    }

    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        Feedback.watchTaps(dialog.getWindow());
        AccentPalette.watchDialog(dialog);
        return dialog;
    }
}
